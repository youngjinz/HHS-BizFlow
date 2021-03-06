package gov.hhs.usas.rest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import gov.hhs.usas.rest.model.CognosReport;
import gov.hhs.usas.rest.model.USASCredentials;
import gov.hhs.usas.rest.model.USASRequest;
import gov.hhs.usas.rest.model.USASResponse;
import gov.hhs.usas.rest.report.service.Properties;

/**
 * @author pvirdi
 *This class acts as REST Client for USA Staffing Cognos - 
 *Prepares Request & Response for REST web service calls for 
 *authentication and to get report data. 
 */
//@Configuration
@Service
public class CognosRESTClient
{
	private static Log log = LogFactory.getLog(CognosRESTClient.class);
	@Autowired
	private Properties properties;
	@Autowired
	private USASRequest usasRequest;
	//@Autowired
	private USASResponse usasResponse;
	@Autowired
	private USASCredentials credentials;
	private CookieManager manager;

	public CognosRESTClient(){
		manager = new CookieManager();
		//Setting up HTTPS protocol compatible with Java 7
		System.setProperty("https.protocols", "TLSv1.1,TLSv1.2");
		try
		{
			SSLContext ctx = SSLContext.getInstance("TLSv1.2");
			ctx.init(null, null, null);
			SSLContext.setDefault(ctx);
		}
		catch (NoSuchAlgorithmException e)
		{
			log.error(e.getMessage() + "::" + e.getCause());
		}
		catch (KeyManagementException e)
		{
			log.error(e.getMessage() + "::" + e.getCause());
		}
	}


	/**
	 * Logs into the USA Staffing Cognos server, requests report data
	 * for specific report and then logs off.
	 * @param report
	 * @return USASResponse
	 */
	public USASResponse processReportDataRequest(CognosReport report)
	{
		this.usasResponse = new USASResponse();
		if(sendLogonRequest().equalsIgnoreCase(properties.getResponseCodeSuccess())){
			//Call sendReportDatarequest() method
			try{
				HttpURLConnection con = sendReportDataRequest(report);

				if (con.getResponseCode() == properties.getHttpStatusOk())
				{
					BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));

					StringBuffer response = new StringBuffer();
					String inputLine;
					while ((inputLine = in.readLine()) != null) {
						response.append(inputLine);
					}
					in.close();

					String responseString = response.toString();

					//Check is the response contains the requested 'request number' or 'vacancy number'
					String searchRequestNumber = "<Request__Number>" + report.getPrompt().getDisplayValue() +"</Request__Number>";
					String searchVacancyNumber = report.getPrompt().getDisplayValue();					

					if(report.getPrompt().getId().equals(properties.getReportPromptRequest())){
						if(response.toString().contains("No Data Available")){//verify response if it contains 'No Data Available', send an error
							usasResponse.setResponse("No Data Available for " + report.getName() + " report [" + report.getPrompt().getDisplayValue() + "]");
							usasResponse.setResponseCode(properties.getHttpSuccessNoContent());
							usasResponse.setErrorMessage(properties.getNoDataException());
						}else if(responseString.contains(searchRequestNumber)){
							usasResponse.setResponse(responseString);
							usasResponse.setResponseCode(con.getResponseCode());						
							usasResponse.setErrorMessage(properties.getResponseCodeSuccess());
						}else {//send request again
							con = sendReportDataRequest(report);
						}
					}
					else if(report.getPrompt().getId().equals(properties.getReportPromptVacancy())){
						if(responseString.equalsIgnoreCase("No Data Available")){//verify response if it contains 'No Data Available', send an error
							usasResponse.setResponse("No Data Available for " + report.getName() + " report [" + report.getPrompt().getDisplayValue() + "]");
							usasResponse.setResponseCode(properties.getHttpSuccessNoContent());
							usasResponse.setErrorMessage(properties.getNoDataException());
						}else{
							usasResponse.setResponse(responseString);
							usasResponse.setResponseCode(con.getResponseCode());						
							usasResponse.setErrorMessage(properties.getResponseCodeSuccess());
						}
					} 
				}
				else
				{
					usasResponse = new USASResponse();
					usasResponse.setResponse("");
					usasResponse.setResponseCode(con.getResponseCode());
					usasResponse.setErrorMessage(properties.getReportDataException() + con.getResponseCode() + ":" + con.getResponseMessage());
				}
			}catch (Exception e){
				usasResponse = new USASResponse();
				usasResponse.setResponse("");
				usasResponse.setResponseCode(properties.getHttpClientErrorBadRequest());
				usasResponse.setErrorMessage(properties.getReportDataException() + e.getMessage() + "::" + e.getCause());
			}finally{
				log.info(usasResponse.getResponse());
				//sendLogoffRequest();
			}
		}else{
			usasResponse = new USASResponse();
			usasResponse.setResponse("");
			usasResponse.setResponseCode(properties.getHttpClientErrorBadRequest());
			usasResponse.setErrorMessage(properties.getConnectionException());
		}
		log.debug(this.usasResponse.toString());
		return usasResponse;
	}

	private HttpURLConnection sendReportDataRequest(CognosReport cognosReport) throws Exception
	{
		log.info("Sending Report Data Request to USAS Cognos server for " + cognosReport.getName() +":" + cognosReport.getPrompt().getDisplayValue());
		HttpURLConnection con = null;		

		String reportURL = this.usasRequest.getServerURL() + properties.getReportDataPath() + cognosReport.getPath();
		this.usasRequest.setPOSTParameters(cognosReport);			

		URL url = new URL(reportURL);

		con = (HttpURLConnection)url.openConnection();

		con.setRequestMethod(this.usasRequest.getRequestMethod());
		con.setRequestProperty(this.usasRequest.getUserAgentProperty(), this.usasRequest.getUserAgent());
		con.setRequestProperty(this.usasRequest.getAcceptLanguageProperty(), this.usasRequest.getAcceptLanguage());
		con.setRequestProperty(this.usasRequest.getContentTypeProperty(), this.usasRequest.getContentType());
		con.setRequestProperty(this.usasRequest.getCookieProperty(), this.usasRequest.getCookie());

		con.setDoOutput(true);
		OutputStream os = con.getOutputStream();
		os.write(this.usasRequest.getPOSTParameters().getBytes());
		os.flush();
		os.close();

		con.connect();
		log.debug("ReportURL: " + reportURL);
		log.debug(this.usasRequest.toString());

		return con;
	}

	/**
	 * Prepares and sends remote request to login to USA Staffing Cognos server
	 */
	@SuppressWarnings("finally")
	public String sendLogonRequest()
	{

		log.info("Connecting to USAS Cognos server...");
		String connectionResponse = "";
		String logonURL = this.usasRequest.getServerURL() + properties.getLogonPath();
		this.usasRequest.setRequestMethod("POST");
		this.usasRequest.setPOSTParameters(properties.getXmlDataLoginTemplate(), this.credentials);
		try
		{			
			this.manager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
			CookieStore cookieJar = this.manager.getCookieStore();
			CookieHandler.setDefault(this.manager);

			URL url = new URL(logonURL);
			HttpURLConnection con = (HttpURLConnection)url.openConnection();

			con.setRequestMethod(this.usasRequest.getRequestMethod());
			con.setRequestProperty(this.usasRequest.getUserAgentProperty(), this.usasRequest.getUserAgent());
			con.setRequestProperty(this.usasRequest.getAcceptLanguageProperty(), this.usasRequest.getAcceptLanguage());
			con.setDoOutput(true);
			OutputStream os = con.getOutputStream();
			os.write(this.usasRequest.getPOSTParameters().getBytes());
			os.flush();
			os.close();
			con.connect();
			con.getContent();			

			log.debug("LogonURL: " + logonURL);
			log.debug(this.usasRequest.toString());
			if (con.getResponseCode() == properties.getHttpStatusOk())
			{
				log.info("Connection Status: SUCCESS");
				createCookieString(cookieJar);//store the cookies for subsequent requests
				connectionResponse = properties.getResponseCodeSuccess();
			}
			else
			{
				log.info("Connection Status: FAIL");
				log.error(properties.getConnectionException() + con.getResponseCode() + ":" + con.getResponseMessage());
				connectionResponse = properties.getResponseCodeConnectionError();
			}
		}
		catch (Exception e)
		{
			log.error(properties.getConnectionException(), e);
			connectionResponse = properties.getResponseCodeConnectionError();
		}finally{
			return connectionResponse;
		}
	}

	/**
	 * Prepares and sends remote request to logoff from USA Staffing Cognos server
	 */
	public void sendLogoffRequest()
	{
		String logoffURL = this.usasRequest.getServerURL() + properties.getLogoffPath();
		this.usasRequest.setRequestMethod("GET");
		try
		{
			URL url = new URL(logoffURL);
			HttpURLConnection con = (HttpURLConnection)url.openConnection();

			con.setRequestMethod(this.usasRequest.getRequestMethod());
			con.setRequestProperty(this.usasRequest.getUserAgentProperty(), this.usasRequest.getUserAgent());
			con.setRequestProperty(this.usasRequest.getAcceptLanguageProperty(), this.usasRequest.getAcceptLanguage());
			con.setRequestProperty(this.usasRequest.getCookieProperty(), this.usasRequest.getCookie());

			log.info("Disconnecting from USAS Cognos server::" + con.getResponseCode() + "::" + con.getResponseMessage());
		}
		catch (MalformedURLException e)
		{
			log.error("An error occurred while trying to disconnect from USA Staffing server. " + e.getMessage() + "::" + e.getCause(), e);
		}
		catch (IOException e)
		{
			log.error("An error occurred while trying to disconnect from USA Staffing server. " + e.getMessage() + "::" + e.getCause(), e);
		}
	}

	/**
	 * @param cookieStore
	 * Manages the cookie to maintain current session
	 */
	private void createCookieString(CookieStore cookieStore)
	{
		StringBuffer cookieString = new StringBuffer();
		for (int i = 0; i < cookieStore.getCookies().size(); i++)
		{
			if (i > 0) {
				cookieString.append(";");
			}
			cookieString.append(((HttpCookie)cookieStore.getCookies().get(i)).toString());
		}
		this.usasRequest.setCookie(cookieString.toString());
	}

	public USASResponse getUsasResponse()
	{
		return this.usasResponse;
	}
}

