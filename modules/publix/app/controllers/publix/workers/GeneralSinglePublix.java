package controllers.publix.workers;

import controllers.publix.GeneralSingleGroupChannel;
import controllers.publix.IPublix;
import controllers.publix.Publix;
import controllers.publix.StudyAssets;
import daos.common.ComponentResultDao;
import daos.common.StudyResultDao;
import exceptions.publix.ForbiddenPublixException;
import exceptions.publix.InternalServerErrorPublixException;
import exceptions.publix.PublixException;
import general.common.StudyLogger;
import models.common.*;
import models.common.workers.GeneralSingleWorker;
import models.common.workers.Worker;
import play.Logger;
import play.Logger.ALogger;
import play.db.jpa.JPAApi;
import play.mvc.Http;
import play.mvc.Result;
import services.publix.PublixErrorMessages;
import services.publix.PublixUtils;
import services.publix.ResultCreator;
import services.publix.WorkerCreator;
import services.publix.idcookie.IdCookieService;
import services.publix.workers.GeneralSingleCookieService;
import services.publix.workers.GeneralSingleStudyAuthorisation;
import utils.common.IOUtils;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Implementation of JATOS' public API for general single study runs (open to
 * everyone). A general single run is done by a GeneralSingleWorker.
 *
 * @author Kristian Lange
 */
@Singleton
public class GeneralSinglePublix extends Publix<GeneralSingleWorker> implements IPublix {

    private static final ALogger LOGGER = Logger.of(GeneralSinglePublix.class);

    private final PublixUtils publixUtils;
    private final GeneralSingleStudyAuthorisation studyAuthorisation;
    private final ResultCreator resultCreator;
    private final WorkerCreator workerCreator;
    private final GeneralSingleCookieService generalSingleCookieService;
    private final StudyLogger studyLogger;

    @Inject
    GeneralSinglePublix(JPAApi jpa, PublixUtils publixUtils,
            GeneralSingleStudyAuthorisation studyAuthorisation,
            ResultCreator resultCreator, WorkerCreator workerCreator,
            GeneralSingleGroupChannel groupChannel,
            IdCookieService idCookieService,
            GeneralSingleCookieService generalSingleCookieService,
            PublixErrorMessages errorMessages, StudyAssets studyAssets,
            JsonUtils jsonUtils, ComponentResultDao componentResultDao,
            StudyResultDao studyResultDao, StudyLogger studyLogger, IOUtils ioUtils) {
        super(jpa, publixUtils, studyAuthorisation, groupChannel,
                idCookieService, errorMessages, studyAssets,
                jsonUtils, componentResultDao, studyResultDao, studyLogger, ioUtils);
        this.publixUtils = publixUtils;
        this.studyAuthorisation = studyAuthorisation;
        this.resultCreator = resultCreator;
        this.workerCreator = workerCreator;
        this.generalSingleCookieService = generalSingleCookieService;
        this.studyLogger = studyLogger;
    }

    /**
     * {@inheritDoc}<br>
     * <br>
     * <p>
     * Only a general single run or a personal single run has the special
     * StudyState PRE. Only with the corresponding workers (GeneralSingleWorker
     * and PersonalSingleWorker) it's possible to have a preview of the study.
     * To get into the preview mode one has to add 'pre' to the URL query
     * string. In the preview mode a worker can start the study (with 'pre') and
     * start the first active component as often as he wants. The study result switches
     * into 'STARTED' and back to normal behavior by starting the study without
     * the 'pre' in the query string or by going on and start a component
     * different then the first.
     */
    @Override
    public Result startStudy(Http.Request request, StudyLink studyLink) throws PublixException {
        Batch batch = studyLink.getBatch();
        Study study = batch.getStudy();
        Long workerId = generalSingleCookieService.fetchWorkerIdByStudy(study);

        // There are 4 possibilities
        // 1. Preview study, first call -> create Worker and StudyResult, call finishOldestStudyResult, write General Single Cookie
        // 2. Preview study, second+ call (same browser) -> get StudyResult, do not call finishOldestStudyResult, do not write General Single Cookie
        // 3. No preview study, first call -> create StudyResult, call finishOldestStudyResult, write General Single Cookie
        // 4. No preview study, second+ call -> throw exception
        // Different browser always leads to a new study run
        StudyResult studyResult;
        Worker worker;
        if (workerId == null) {
            worker = workerCreator.createAndPersistGeneralSingleWorker(batch);
            studyAuthorisation.checkWorkerAllowedToStartStudy(request, worker, study, batch);
            publixUtils.finishOldestStudyResult();
            studyResult = resultCreator.createStudyResult(studyLink, worker);
            generalSingleCookieService.set(study, worker);
            studyLogger.log(studyLink, "Started study run with " + GeneralSingleWorker.UI_WORKER_TYPE
                    + " worker", worker);
        } else {
            worker = publixUtils.retrieveWorker(workerId);
            if (worker == null) throw new ForbiddenPublixException(PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
            studyAuthorisation.checkWorkerAllowedToStartStudy(request, worker, study, batch);
            studyResult = worker.getLastStudyResult().orElseThrow(() -> new InternalServerErrorPublixException(
                    "Repeated study run but couldn't find last study result"));
            if (!idCookieService.hasIdCookie(studyResult.getId())) {
                publixUtils.finishOldestStudyResult();
                generalSingleCookieService.set(study, worker);
            }
        }
        idCookieService.writeIdCookie(studyResult);
        publixUtils.setUrlQueryParameter(request, studyResult);
        Component firstComponent = publixUtils.retrieveFirstActiveComponent(study);

        LOGGER.info(".startStudy: studyLinkId " + studyLink.getId() + ", "
                + "studyResultId" + studyResult.getId() + ", "
                + "studyId " + study.getId() + ", "
                + "batchId " + batch.getId() + ", "
                + "workerId " + worker.getId());
        return redirect(controllers.publix.routes.PublixInterceptor.startComponent(
                studyResult.getUuid(), firstComponent.getUuid(), null));
    }

}
