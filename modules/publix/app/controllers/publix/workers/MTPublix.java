package controllers.publix.workers;

import controllers.publix.*;
import daos.common.ComponentResultDao;
import daos.common.StudyResultDao;
import daos.common.worker.MTWorkerDao;
import exceptions.publix.BadRequestPublixException;
import exceptions.publix.PublixException;
import general.common.StudyLogger;
import models.common.*;
import models.common.workers.MTSandboxWorker;
import models.common.workers.MTWorker;
import play.Logger;
import play.Logger.ALogger;
import play.db.jpa.JPAApi;
import play.mvc.Http;
import play.mvc.Result;
import scala.Some;
import services.publix.*;
import services.publix.idcookie.IdCookieService;
import services.publix.workers.MTStudyAuthorisation;
import utils.common.Helpers;
import utils.common.IOUtils;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

/**
 * Implementation of JATOS' public API for studies that are started via MTurk. A
 * MTurk run is done by a MTWorker or a MTSandboxWorker.
 *
 * @author Kristian Lange
 */
@Singleton
public class MTPublix extends Publix<MTWorker> implements IPublix {

    private static final ALogger LOGGER = Logger.of(MTPublix.class);

    private final PublixUtils publixUtils;
    private final MTStudyAuthorisation studyAuthorisation;
    private final ResultCreator resultCreator;
    private final WorkerCreator workerCreator;
    private final MTWorkerDao mtWorkerDao;
    private final StudyLogger studyLogger;

    @Inject
    MTPublix(JPAApi jpa, PublixUtils publixUtils,
            MTStudyAuthorisation studyAuthorisation,
            ResultCreator resultCreator, WorkerCreator workerCreator,
            MTGroupChannel groupChannel, IdCookieService idCookieService,
            PublixErrorMessages errorMessages, StudyAssets studyAssets,
            JsonUtils jsonUtils, ComponentResultDao componentResultDao,
            StudyResultDao studyResultDao, MTWorkerDao mtWorkerDao, StudyLogger studyLogger, IOUtils ioUtils) {
        super(jpa, publixUtils, studyAuthorisation,
                groupChannel, idCookieService,
                errorMessages, studyAssets, jsonUtils, componentResultDao,
                studyResultDao, studyLogger, ioUtils);
        this.publixUtils = publixUtils;
        this.studyAuthorisation = studyAuthorisation;
        this.resultCreator = resultCreator;
        this.workerCreator = workerCreator;
        this.mtWorkerDao = mtWorkerDao;
        this.studyLogger = studyLogger;
    }

    @Override
    public Result startStudy(Http.Request request, StudyLink studyLink) throws PublixException {
        // Get MTurk query parameters
        // Hint: Don't confuse MTurk's workerId with JATOS' workerId. MTurk's workerId is generated by MTurk and stored
        // within the MTWorker as mtWorkerId. MTurk's workerId is used to identify a worker in MTurk. JATOS' workerId
        // is used to identify a worker in JATOS.
        String mtWorkerId = Helpers.getQueryString("workerId");
        String mtAssignmentId = Helpers.getQueryString("assignmentId");
        String requestsWorkerType = retrieveWorkerTypeFromQueryString(request);
        Batch batch = studyLink.getBatch();
        Study study = batch.getStudy();

        // Check if it's just a preview coming from MTurk. We don't allow MTurk previews.
        if (mtAssignmentId != null && mtAssignmentId.equals("ASSIGNMENT_ID_NOT_AVAILABLE")) {
            // It's a preview coming from Mechanical Turk -> no previews
            throw new BadRequestPublixException("No preview available for study " + study.getId() + ".");
        }

        // Check worker and create if doesn't exists
        if (mtWorkerId == null) {
            throw new BadRequestPublixException("MTurk's workerId is missing in the query parameters.");
        }
        Optional<MTWorker> workerOptional = mtWorkerDao.findByMTWorkerId(mtWorkerId);
        MTWorker worker;
        if (!workerOptional.isPresent()) {
            boolean isRequestFromMTurkSandbox = requestsWorkerType.equals(MTSandboxWorker.WORKER_TYPE);
            worker = workerCreator.createAndPersistMTWorker(mtWorkerId, isRequestFromMTurkSandbox, batch);
        } else {
            worker = workerOptional.get();
        }

        // Check if same worker type MT/MTSandbox (can only happen in non-legitimate cases)
        if (!worker.getWorkerType().equals(requestsWorkerType)) {
            throw new BadRequestPublixException("Wrong worker type: A worker with this MTurk workerId=" + mtWorkerId
                    + " exists already in JATOS. But the existing worker is of type " + worker.getWorkerType()
                    + " while your study link is for type " + studyLink.getWorkerType() + ".");
        }
        studyAuthorisation.checkWorkerAllowedToStartStudy(request, worker, study, batch);

        publixUtils.finishOldestStudyResult();
        StudyResult studyResult = resultCreator.createStudyResult(studyLink, worker);
        publixUtils.setUrlQueryParameter(request, studyResult);
        idCookieService.writeIdCookie(studyResult);
        Component firstComponent = publixUtils.retrieveFirstActiveComponent(study);

        LOGGER.info(".startStudy: studyLinkId " + studyLink.getId() + ", "
                + "studyResultId" + studyResult.getId() + ", "
                + "studyId " + study.getId() + ", "
                + "batchId " + batch.getId() + ", "
                + "workerId " + worker.getId());
        studyLogger.log(studyLink, "Started study run with " + MTWorker.UI_WORKER_TYPE + " worker", worker);
        return redirect(controllers.publix.routes.PublixInterceptor.startComponent(
                studyResult.getUuid(), firstComponent.getUuid(), null));
    }

    @Override
    public Result finishStudy(Http.Request request, StudyResult studyResult, Boolean successful, String message)
            throws PublixException {
        Study study = studyResult.getStudy();
        Batch batch = studyResult.getBatch();
        MTWorker worker = (MTWorker) studyResult.getWorker();
        studyAuthorisation.checkWorkerAllowedToDoStudy(request, worker, study, batch);

        String confirmationCode;
        if (!PublixHelpers.studyDone(studyResult)) {
            confirmationCode = publixUtils.finishStudyResult(successful, message, studyResult);
            groupChannel.closeGroupChannelAndLeaveGroup(studyResult);
        } else {
            confirmationCode = studyResult.getConfirmationCode();
        }
        idCookieService.discardIdCookie(studyResult.getId());
        studyLogger.log(study, "Finished study run", worker);

        if (Helpers.isAjax()) {
            return ok(confirmationCode);
        } else {
            if (!successful) {
                return ok(views.html.publix.error.render(message));
            } else {
                return redirect(routes.StudyAssets.endPage(study.getId(), new Some<>(confirmationCode)));
            }
        }
    }

    /**
     * Returns either MTSandboxWorker.WORKER_TYPE or MTWorker.WORKER_TYPE. It depends on the URL query string. Returns
     * MTSandboxWorker if the query string has the parameter 'turkSubmitTo' and its value contains 'sandbox'.
     * Otherwise returns MTWorker.
     */
    private String retrieveWorkerTypeFromQueryString(Http.Request request) {
        String turkSubmitTo = request.getQueryString("turkSubmitTo");
        if (turkSubmitTo != null && turkSubmitTo.toLowerCase().contains("sandbox")) {
            return MTSandboxWorker.WORKER_TYPE;
        } else {
            return MTWorker.WORKER_TYPE;
        }
    }

}
