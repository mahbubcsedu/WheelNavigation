package com.mpss.wheelnav;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.mpss.wheelnav.model.AccessibilityIssueRequest;
import com.mpss.wheelnav.model.DatabaseHandler;
import com.mpss.wheelnav.model.IssueRequest;
import com.mpss.wheelnav.model.UploadToServerResponse;
import com.mpss.wheelnav.utility.Base64;
import com.mpss.wheelnav.utility.DataLoader;

public class HTTPRequestManager {


	public static final String PREFS_NAME = "AccessibilityPreferences";
	public static final String DEVICE_ID_KEY = "DEVICE_UUID";

	private String upload_url =""; 
	private DefaultHttpClient mHttpClient;
	//private createHttpClient mHttpClient;
	//HttpClient mHttpClient;
	Context context;

	public HTTPRequestManager(String url, Context context) {

		upload_url=url;
		HttpParams params = new BasicHttpParams();
		params.setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
		mHttpClient = new DefaultHttpClient(params);
		
		//mHttpClient = createHttpClient();
		this.context = context;
	}
	/*private HttpClient createHttpClient()
	{
	    HttpParams params = new BasicHttpParams();
	    HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
	    HttpProtocolParams.setContentCharset(params, HTTP.DEFAULT_CONTENT_CHARSET);
	    HttpProtocolParams.setUseExpectContinue(params, true);

	    SchemeRegistry schReg = new SchemeRegistry();
	    schReg.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
	    schReg.register(new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));
	    ClientConnectionManager conMgr = new ThreadSafeClientConnManager(params, schReg);

	    return new DefaultHttpClient(conMgr, params);
	}*/
	public boolean uploadNewRequest(AccessibilityIssueRequest issueRequest) {

		try {      

			IssueRequest request = convertAccessibilityIssueRequest(issueRequest);
			int requestId = saveRequestToDB(request);

			if(requestId==-1) {
				return false;
				//TODO: return 
			}

			HttpPost httpPostRequest = createHttpRequest(request);

			if(httpPostRequest==null) {
				return false;
				//TODO: return 
			}	

			UploadToServerResponse responseObj = postRequest(httpPostRequest);

			if(responseObj==null) {
				return false;
				//TODO: return 
			}

			return processResponse(requestId, responseObj);

		} catch (Exception e) {
			Log.e(HTTPRequestManager.class.getName(), e.getLocalizedMessage(), e);
		}
		return false;
	}

	public boolean processResponse(int requestId, UploadToServerResponse responseObj) {

		if(responseObj.getDeviecUUID()!=null && responseObj.getDeviecUUID().trim()!="" && !isDeviceUUIDAvailable()) {
			writeDeviceUUID(responseObj.getDeviecUUID());
		}

		if(responseObj.isSuccess()) {
			updateRequestStatus(requestId, true);
		}

		//Toast.makeText(context, responseObj.getErrorMessage(), Toast.LENGTH_LONG).show();
		//TODO: what to do with error message

		return responseObj.isSuccess();
	}

	public HttpPost createHttpRequest(IssueRequest issueRequest) {

		Bitmap bitmap = BitmapFactory.decodeFile(issueRequest.get_annotatedImageFilePath());
		ByteArrayOutputStream stream = new ByteArrayOutputStream();		
		bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream); //compress to which format you want.		
		byte [] byte_arr = stream.toByteArray();		
		String image_str = Base64.encodeBytes(byte_arr);

		File faudio=null;
		if(issueRequest.get_voiceCommentFilePath()!=null && issueRequest.get_voiceCommentFilePath().trim()!="") {
			faudio=new File(issueRequest.get_voiceCommentFilePath());
		}

		HttpPost httppost = new HttpPost(upload_url);
		MultipartEntity multipartEntity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);
		try {

			if(isDeviceUUIDAvailable()) {
				multipartEntity.addPart("DEVICE_UUID", new StringBody(getDeviceUUID()));

			}

			multipartEntity.addPart("IMAGE", new StringBody(image_str));
			multipartEntity.addPart("IMAGE_HEIGHT", new StringBody(Integer.toString(bitmap.getHeight())));
			multipartEntity.addPart("IMAGE_WIDTH", new StringBody(Integer.toString(bitmap.getWidth())));

			if(faudio!=null) {
				if(issueRequest.get_voiceCommentFilePath()!=""){
					multipartEntity.addPart("VOICE_COMMENT", new FileBody(faudio));
				}
			}
			multipartEntity.addPart("ISSUE_TYPE", new StringBody(issueRequest.get_issueType()));
			multipartEntity.addPart("DISTANCE_TO_SUBJECT", new StringBody(Float.toString(issueRequest.get_distanceToSubject())));
			multipartEntity.addPart("DISTANCE_UNIT", new StringBody(issueRequest.get_distanceUnit()));
			multipartEntity.addPart("BOUNDING_BOX_LEFT", new StringBody(Integer.toString(issueRequest.get_boundingBoxLeft())));
			multipartEntity.addPart("BOUNDING_BOX_TOP", new StringBody(Integer.toString(issueRequest.get_boundingBoxTop())));
			multipartEntity.addPart("BOUNDING_BOX_RIGHT", new StringBody(Integer.toString(issueRequest.get_boundingBoxRight())));
			multipartEntity.addPart("BOUNDING_BOX_BOTTOM", new StringBody(Integer.toString(issueRequest.get_boundingBoxBottom())));
			multipartEntity.addPart("LOCATION_LATITUDE", new StringBody(issueRequest.get_locationLatitude()));
			multipartEntity.addPart("LOCATION_LONGITUDE", new StringBody(issueRequest.get_locationLongitude()));
			multipartEntity.addPart("COMPASS_AZIMUTH", new StringBody(issueRequest.get_compassAzimuthValue()));

			if(issueRequest.get_textComment()!=null)
				multipartEntity.addPart("TEXT_COMMENT", new StringBody(issueRequest.get_textComment()));

			if(issueRequest.get_nearFocusDistance()!=null)
				multipartEntity.addPart("NEAR_FOCUS_DISTANCE", new StringBody(issueRequest.get_nearFocusDistance()));
			if(issueRequest.get_optimalFocusDistance()!=null)
				multipartEntity.addPart("OPTIMAL_FOCUS_DISTANCE", new StringBody(issueRequest.get_optimalFocusDistance()));
			if(issueRequest.get_farFocusDistance()!=null)
				multipartEntity.addPart("FAR_FOCUS_DISTANCE", new StringBody(issueRequest.get_farFocusDistance()));

			if(issueRequest.get_aperature_EXIF()!=null)
				multipartEntity.addPart("APERATURE_EXIF", new StringBody(issueRequest.get_aperature_EXIF()));
			if(issueRequest.get_datetime_EXIF()!=null)
				multipartEntity.addPart("DATE_TIME_EXIF", new StringBody(issueRequest.get_datetime_EXIF()));
			if(issueRequest.get_exposureTime_EXIF()!=null)
				multipartEntity.addPart("EXPOSURE_TIME_EXIF", new StringBody(issueRequest.get_exposureTime_EXIF()));
			if(issueRequest.get_flash_EXIF()!=null)
				multipartEntity.addPart("FLASH_EXIF", new StringBody(issueRequest.get_flash_EXIF()));
			if(issueRequest.get_focalLength_EXIF()!=null)
				multipartEntity.addPart("FOCAL_LENGTH_EXIF", new StringBody(issueRequest.get_focalLength_EXIF()));

			if(issueRequest.get_iso_EXIF()!=null)
				multipartEntity.addPart("ISO_EXIF", new StringBody(issueRequest.get_iso_EXIF()));
			if(issueRequest.get_make_EXIF()!=null)
				multipartEntity.addPart("MAKE_EXIF", new StringBody(issueRequest.get_make_EXIF()));
			if(issueRequest.get_model_EXIF()!=null)
				multipartEntity.addPart("MODEL_EXIF", new StringBody(issueRequest.get_model_EXIF()));
			if(issueRequest.get_orientation_EXIF()!=null)
				multipartEntity.addPart("ORIENTATION_EXIF", new StringBody(issueRequest.get_orientation_EXIF()));
			if(issueRequest.get_whiteBalance_EXIF()!=null)
				multipartEntity.addPart("WHITE_BALANCE_EXIF", new StringBody(issueRequest.get_whiteBalance_EXIF()));

			httppost.setEntity(multipartEntity);
			return httppost;

		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return null;
	}

	public UploadToServerResponse postRequest(HttpPost postRequest) {

		try {

			//HttpResponse response = mHttpClient.execute(postRequest);
			DataLoader dl=new DataLoader();
			HttpResponse response;
			try {
				response = dl.secureLoadData(postRequest);
				if(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {

					String responseString = EntityUtils.toString(response.getEntity());
					JSONObject obj = new JSONObject(responseString);
					UploadToServerResponse responseObj = null;

					if(obj.has("response")) {

						JSONObject jSonResponse = obj.getJSONObject("response");
						responseObj = new UploadToServerResponse();

						if(jSonResponse.has("success")) {
							responseObj.setSuccess(jSonResponse.getBoolean("success"));
						}
						if(jSonResponse.has("error")) {
							responseObj.setErrorMessage(jSonResponse.getString("error"));
						}
						if(jSonResponse.has("UUID")) {
							responseObj.setDeviecUUID(jSonResponse.getString("UUID"));
						}
					}
					return responseObj;
				}
			} catch (KeyManagementException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (UnrecoverableKeyException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (NoSuchAlgorithmException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (KeyStoreException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (URISyntaxException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			

		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return null;
	}



	private IssueRequest convertAccessibilityIssueRequest(AccessibilityIssueRequest issueRequest) {
		IssueRequest request = new IssueRequest();

		request.set_annotatedImageFilePath(issueRequest.getAnnotatedImageFilePath()); 

		request.set_boundingBoxLeft(issueRequest.getBoundingBoxLeft());
		request.set_boundingBoxTop(issueRequest.getBoundingBoxTop());
		request.set_boundingBoxRight(issueRequest.getBoundingBoxRight());
		request.set_boundingBoxBottom(issueRequest.getBoundingBoxBottom());

		request.set_voiceCommentFilePath(issueRequest.getVoiceCommentFilePath());
		request.set_textComment(issueRequest.getTextComment());

		request.set_locationLatitude(issueRequest.getLocationLatitude());
		request.set_locationLongitude(issueRequest.getLocationLongitude());
		request.set_compassAzimuthValue(issueRequest.getCompassAzimuthValue());

		request.set_issueType(issueRequest.getIssueType());

		request.set_distanceToSubject(issueRequest.getDistanceToSubject()); 
		request.set_distanceUnit(issueRequest.getDistanceUnit()); 

		request.set_aperature_EXIF(issueRequest.getAperature_EXIF());
		request.set_datetime_EXIF(issueRequest.getDatetime_EXIF());
		request.set_exposureTime_EXIF(issueRequest.getExposureTime_EXIF());
		request.set_flash_EXIF(issueRequest.getFlash_EXIF());
		request.set_focalLength_EXIF(issueRequest.getFocalLength_EXIF());

		request.set_gpsLatitude_EXIF(issueRequest.getGpsLatitude_EXIF());
		request.set_gpsLatitudeRef_EXIF(issueRequest.getGpsLatitudeRef_EXIF());
		request.set_gpsLongitude_EXIF(issueRequest.getGpsLongitude_EXIF());
		request.set_gpsLongitudeRef_EXIF(issueRequest.getGpsLongitudeRef_EXIF());
		request.set_gpsTimestamp_EXIF(issueRequest.getGpsTimestamp_EXIF());

		request.set_iso_EXIF(issueRequest.getIso_EXIF());
		request.set_make_EXIF(issueRequest.getMake_EXIF());
		request.set_model_EXIF(issueRequest.getModel_EXIF());
		request.set_orientation_EXIF(issueRequest.getOrientation_EXIF());
		request.set_whiteBalance_EXIF(issueRequest.getWhiteBalance_EXIF());    

		request.set_DateCaptured(issueRequest.getDateCaptured());

		request.set_nearFocusDistance(issueRequest.getNearFocusDistance());
		request.set_optimalFocusDistance(issueRequest.getOptimalFocusDistance());
		request.set_farFocusDistance(issueRequest.getFarFocusDistance());

		return request;
	}

	private int saveRequestToDB(IssueRequest request) {

		DatabaseHandler db = new DatabaseHandler(context);
		request.set_UploadedToServer(false);
		long id = db.addRequest(request);
		return (int)id;
	}

	private int updateRequestStatus(int requestId, boolean status) {

		DatabaseHandler db = new DatabaseHandler(context);
		IssueRequest request = db.getRequest(requestId);
		request.set_UploadedToServer(status);
		return db.updateRequest(request);
	}

	private boolean isDeviceUUIDAvailable() {

		SharedPreferences settings = context.getSharedPreferences(PREFS_NAME, 0);
		return settings.contains(DEVICE_ID_KEY);
	}

	private void writeDeviceUUID(String deviceUUID) {

		SharedPreferences settings = context.getSharedPreferences(PREFS_NAME, 0);
		SharedPreferences.Editor editor = settings.edit();
		editor.putString(DEVICE_ID_KEY, deviceUUID);
		editor.commit();
	}

	private String getDeviceUUID() {

		SharedPreferences settings = context.getSharedPreferences(PREFS_NAME, 0);
		return settings.getString(DEVICE_ID_KEY, "");
	}

}