import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static play.mvc.Http.Status.OK;
import static play.mvc.Http.Status.SEE_OTHER;
import static play.test.Helpers.callAction;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.fakeRequest;
import static play.test.Helpers.headers;
import static play.test.Helpers.session;
import static play.test.Helpers.status;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import models.ComponentModel;
import models.StudyModel;

import org.apache.http.HttpHeaders;
import org.junit.Test;

import play.db.jpa.JPA;
import play.mvc.Result;
import play.test.FakeRequest;
import services.Breadcrumbs;
import services.IOUtils;
import common.Initializer;
import controllers.Components;
import controllers.Users;
import controllers.publix.jatos.JatosPublix;
import exceptions.ResultException;

/**
 * Testing actions of controller.Components.
 * 
 * @author Kristian Lange
 */
public class ComponentsControllerTest extends AbstractControllerTest {

	/**
	 * Checks action with route Components.showComponent.
	 */
	@Test
	public void callShowComponent() throws Exception {
		StudyModel studyClone = cloneStudy();

		Result result = callAction(
				controllers.routes.ref.Components.showComponent(
						studyClone.getId(), studyClone.getComponent(1).getId()),
				fakeRequest().withSession(Users.SESSION_EMAIL,
						Initializer.ADMIN_EMAIL));
		assertEquals(SEE_OTHER, status(result));
		assert session(result).containsKey(JatosPublix.JATOS_SHOW);
		assert session(result).containsValue(JatosPublix.SHOW_COMPONENT_START);
		assert session(result).containsKey(JatosPublix.SHOW_COMPONENT_ID);
		assert session(result).containsValue(studyClone.getId().toString());
		assert headers(result).get(HttpHeaders.LOCATION).contains(
				JatosPublix.JATOS_WORKER_ID);

		// Clean up
		removeStudy(studyClone);
	}

	/**
	 * Checks action with route Components.showComponent if no html file is set
	 * within the Component.
	 */
	@Test
	public void callShowComponentNoHtml() throws Exception {
		StudyModel studyClone = cloneStudy();

		JPA.em().getTransaction().begin();
		studyClone.getComponent(1).setHtmlFilePath(null);
		JPA.em().getTransaction().commit();

		try {
			callAction(
					controllers.routes.ref.Components.showComponent(studyClone
							.getId(), studyClone.getComponent(1).getId()),
					fakeRequest().withSession(Users.SESSION_EMAIL,
							Initializer.ADMIN_EMAIL));
		} catch (RuntimeException e) {
			assert (e.getCause() instanceof ResultException);
		} finally {
			removeStudy(studyClone);
		}
	}

	/**
	 * Checks action with route Components.create.
	 */
	@Test
	public void callCreate() throws IOException {
		StudyModel studyClone = cloneStudy();

		Result result = callAction(
				controllers.routes.ref.Components.create(studyClone.getId()),
				fakeRequest().withSession(Users.SESSION_EMAIL,
						Initializer.ADMIN_EMAIL));
		assertThat(status(result)).isEqualTo(OK);
		assertThat(contentAsString(result)).contains(Breadcrumbs.NEW_COMPONENT);

		// Clean up
		removeStudy(studyClone);
	}

	/**
	 * Checks action with route Components.submit. After the call the study's
	 * page should be shown.
	 */
	@Test
	public void callSubmit() throws Exception {
		StudyModel studyClone = cloneStudy();

		Map<String, String> form = new HashMap<String, String>();
		form.put(ComponentModel.TITLE, "Title Test");
		form.put(ComponentModel.RELOADABLE, "true");
		form.put(ComponentModel.HTML_FILE_PATH, "html_file_path_test.html");
		form.put(ComponentModel.COMMENTS, "Comments test test.");
		form.put(ComponentModel.JSON_DATA, "{}");
		form.put(Components.EDIT_SUBMIT_NAME, Components.EDIT_SUBMIT);
		FakeRequest request = fakeRequest().withSession(Users.SESSION_EMAIL,
				Initializer.ADMIN_EMAIL).withFormUrlEncodedBody(form);
		Result result = callAction(
				controllers.routes.ref.Components.submit(studyClone.getId()),
				request);
		assertEquals(SEE_OTHER, status(result));

		// Clean up
		removeStudy(studyClone);
	}

	/**
	 * Checks action with route Components.submit. After the call the component
	 * itself should be shown.
	 */
	@Test
	public void callSubmitAndShow() throws Exception {
		StudyModel studyClone = cloneStudy();

		Map<String, String> form = new HashMap<String, String>();
		form.put(ComponentModel.TITLE, "Title Test");
		form.put(ComponentModel.RELOADABLE, "true");
		form.put(ComponentModel.HTML_FILE_PATH, "html_file_path_test.html");
		form.put(ComponentModel.COMMENTS, "Comments test test.");
		form.put(ComponentModel.JSON_DATA, "{}");
		form.put(Components.EDIT_SUBMIT_NAME, Components.EDIT_SUBMIT_AND_SHOW);
		FakeRequest request = fakeRequest().withSession(Users.SESSION_EMAIL,
				Initializer.ADMIN_EMAIL).withFormUrlEncodedBody(form);
		Result result = callAction(
				controllers.routes.ref.Components.submit(studyClone.getId()),
				request);
		assertEquals(SEE_OTHER, status(result));
		headers(result).get(HttpHeaders.LOCATION).contains("show");

		// Clean up
		removeStudy(studyClone);
	}

	/**
	 * Checks action with route Components.submit with validation error.
	 */
	@Test
	public void callSubmitValidationError() throws Exception {
		StudyModel studyClone = cloneStudy();

		Map<String, String> form = new HashMap<String, String>();
		form.put(ComponentModel.TITLE, "");
		form.put(ComponentModel.RELOADABLE, "true");
		form.put(ComponentModel.JSON_DATA, "{");
		form.put(Components.EDIT_SUBMIT_NAME, Components.EDIT_SUBMIT_AND_SHOW);
		FakeRequest request = fakeRequest().withSession(Users.SESSION_EMAIL,
				Initializer.ADMIN_EMAIL).withFormUrlEncodedBody(form);
		try {
			callAction(controllers.routes.ref.Components.submit(studyClone
					.getId()), request);
		} catch (RuntimeException e) {
			assert (e.getCause() instanceof ResultException);
		} finally {
			removeStudy(studyClone);
		}
	}

	@Test
	public void callChangeProperties() throws Exception {
		StudyModel studyClone = cloneStudy();

		FakeRequest request = fakeRequest().withSession(Users.SESSION_EMAIL,
				Initializer.ADMIN_EMAIL);
		Result result = callAction(
				controllers.routes.ref.Components.changeProperty(
						studyClone.getId(), studyClone.getComponent(1).getId(),
						true), request);
		assertThat(status(result)).isEqualTo(OK);

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void callCloneComponent() throws Exception {
		StudyModel studyClone = cloneStudy();

		FakeRequest request = fakeRequest().withSession(Users.SESSION_EMAIL,
				Initializer.ADMIN_EMAIL);
		Result result = callAction(
				controllers.routes.ref.Components.cloneComponent(
						studyClone.getId(), studyClone.getComponent(1).getId()),
				request);
		assertThat(status(result)).isEqualTo(SEE_OTHER);

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void callRemove() throws Exception {
		StudyModel studyClone = cloneStudy();

		FakeRequest request = fakeRequest().withSession(Users.SESSION_EMAIL,
				Initializer.ADMIN_EMAIL);
		Result result = callAction(controllers.routes.ref.Components.remove(
				studyClone.getId(), studyClone.getComponent(1).getId()),
				request);
		assertThat(status(result)).isEqualTo(OK);

		// Clean up - can't remove study due to some RollbackException. No idea
		// why. At least remove study assets dir.
		// removeStudy(studyClone);
		IOUtils.removeStudyAssetsDir(studyClone.getDirName());
	}

}