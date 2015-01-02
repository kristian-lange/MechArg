package common;

import java.io.File;
import java.util.List;
import java.util.UUID;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import play.Logger;
import play.db.jpa.JPA;
import services.PersistanceUtils;
import controllers.publix.StudyAssets;

public class Initializer {

	private static final String CLASS_NAME = Initializer.class.getSimpleName();

	public static void initialize() {
		checkAdmin();
		checkUuid();
		checkStudyAssetsDir();
	}

	/**
	 * Check whether studies assets directory exists and create if not.
	 */
	private static void checkStudyAssetsDir() {
		boolean success = new File(StudyAssets.STUDY_ASSETS_PATH).mkdir();
		if (success) {
			Logger.info(CLASS_NAME
					+ ".checkStudyAssetsDir: Created study assets directory "
					+ StudyAssets.STUDY_ASSETS_PATH);
		}
	}

	/**
	 * Migration from older DB schema: generate UUID for all studies/components.
	 */
	private static void checkUuid() {
		JPA.withTransaction(new play.libs.F.Callback0() {
			@Override
			public void invoke() throws Throwable {
				List<StudyModel> studyModelList = StudyModel.findAll();
				for (StudyModel study : studyModelList) {
					if (study.getUuid() == null || study.getUuid().isEmpty()) {
						study.setUuid(UUID.randomUUID().toString());
						study.merge();
					}
					for (ComponentModel component : study.getComponentList()) {
						if (component.getUuid() == null
								|| component.getUuid().isEmpty()) {
							component.setUuid(UUID.randomUUID().toString());
							component.merge();
						}
					}
				}
			}
		});
	}

	/**
	 * Check for user admin: In case the app is started the first time we need
	 * an initial user: admin. If admin can't be found, create one.
	 */
	private static void checkAdmin() {
		JPA.withTransaction(new play.libs.F.Callback0() {
			@Override
			public void invoke() throws Throwable {
				UserModel admin = UserModel.findByEmail("admin");
				if (admin == null) {
					PersistanceUtils.createAdmin();
				}
			}
		});
	}

}
