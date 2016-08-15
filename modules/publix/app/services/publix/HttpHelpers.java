package services.publix;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;

import org.w3c.dom.Document;

import com.fasterxml.jackson.databind.JsonNode;

import exceptions.publix.UnsupportedMediaTypePublixException;
import play.mvc.Http.RequestBody;
import utils.common.XMLUtils;

/**
 * @author Kristian Lange
 */
public class HttpHelpers {

	/**
	 * Retrieves the text from the request body and returns it as a String. If
	 * the content is in JSON or XML format it's parsed to bring the String into
	 * a nice format. If the content is neither text nor JSON or XML an
	 * UnsupportedMediaTypePublixException is thrown.
	 */
	public static String getDataFromRequestBody(RequestBody requestBody)
			throws UnsupportedMediaTypePublixException {
		// Text
		String text = requestBody.asText();
		if (text != null) {
			return text;
		}

		// JSON
		JsonNode json = requestBody.asJson();
		if (json != null) {
			return json.toString();
		}

		// XML
		Document xml = requestBody.asXml();
		if (xml != null) {
			return XMLUtils.asString(xml);
		}

		// No supported format
		throw new UnsupportedMediaTypePublixException(
				PublixErrorMessages.SUBMITTED_DATA_UNKNOWN_FORMAT);
	}

	public static String urlEncode(String str) {
		String encodedStr = "";
		try {
			encodedStr = URLEncoder.encode(str, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			// Do nothing
		}
		return encodedStr;
	}
	
	public static String urlDecode(String str) {
		String decodedStr = "";
		try {
			decodedStr = URLDecoder.decode(str, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			// Do nothing
		}
		return decodedStr;
	}

}
