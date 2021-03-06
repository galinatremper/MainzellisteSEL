package de.securerecordlinkage;

import de.securerecordlinkage.configuration.ConfigLoader;
import de.securerecordlinkage.helperClasses.*;
import de.sessionTokenSimulator.PatientRecords;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.*;

//TODO: Verify against APIkey
//TODO: Extract PatientRecords class, to use this class independent of Mainzelliste
@Path("Communicator")
public class CommunicatorResource {

    private static Logger logger = Logger.getLogger("de.securerecordlinkage.CommunicatorResource");

    // 2a. Re-send linkRecord to SRL
    // 2b. Process callback from SRL (linkRecord)
    // 4a. Process Request from SRL (getAllRecords)
    // 4b. Send Request to ML to get all records
    // 4c. Send all Records to SRL

    //TODO: from config
    private int pageSize = 50;
    private int toDate = 0;
    private int page = 1;

    private static String localId;
    private static String remoteId;
    private static String baseCommunicatorURL = "http://localhost:8082/";
    private static String baseLinkageServiceURL = "http://0.0.0.0:5000";

    private static String localCallbackLinkURL = "http://localhost:8082/Communicator/linkCallBack";
    private static String localCallbackMatchURL = "http://localhost:8082/Communicator/matchCallBack";
    private static String localDataServiceURL = "http://localhost:8082/Communicator/getAllRecords";

    private static String localApiKey = "test123";
    private static List<String> authenticationKeys = new ArrayList<>();
    private static String authenticationType = "authenticationKeys";

    public static String linkRequestURL = "http://192.168.0.101:8080/linkRecord/dkfz";
    public static String linkAllRequestURL = "http://192.168.0.101:8080/linkRecords/dkfz";

    // Read config with SRL links to know where to send the request
    //TODO: make init non static to communicate with X partners
    public static void init(ConfigLoader config, String id) {
        logger.info("Load config variables for communicator");

        localId = config.getLocalID();
        remoteId = id;
//        baseCommunicatorURL = config.getServers().get(remoteId).getUrl();
        baseLinkageServiceURL = config.getServers().get(remoteId).getLinkageServiceBaseURL();
        localCallbackLinkURL = config.getLocalCallbackLinkUrl();
        localCallbackMatchURL = config.getLocalCallbackMatchUrl();
        localDataServiceURL = config.getLocalDataServiceUrl();
        baseCommunicatorURL = localDataServiceURL;
        //TODO: Better name, because in the future this should not only be keys, also other authentication values
        authenticationKeys.add(config.getLocalApiKey());
        localApiKey = config.getLocalApiKey();

        authenticationType = config.getLocalAuthenticationType();


        logger.info("remoteID: " + remoteId + " baseCommunicatorURL: " + localDataServiceURL);

    }

    //-----------------------------------------------------------------------

    /**
     * send linkRecord, which should be linked, to SRL - In Architectur-XML (v6) step 2
     */
    //TODO: change idType and idString to map params
    public void sendLinkRecord(String url, String idType, String idString, JSONObject recordAsJson) {
        logger.info("sendLinkRecord");
        try {

            JSONObject recordToSendJSON = new JSONObject();
            JSONObject callbackObj = getCallBackURLasJSON(idType, idString);

            recordToSendJSON.put("callback", callbackObj);
            recordToSendJSON.put("fields", recordAsJson.get("fields"));

            sendHTTPPOSTWithAuthorizationHeader(url, recordToSendJSON);

        } catch (Exception e) {
            logger.error(e);
        }
    }


    /**
     * send linkRecord, which should be linked, to SRL - In Architectur-XML (v6) step 2
     */
    //TODO: change idType and idString to map params
    public void sendLinkRecords(String url, String idType, JSONArray recordsAsJson) {
        logger.info("sendLinkRecords");
        try {
            JSONObject recordToSendJSON = new JSONObject();
            JSONObject callbackObj = getCallBackURLasJSON(idType, "");

            recordToSendJSON.put("callback", callbackObj);
            recordToSendJSON.put("total", recordsAsJson.length());
            recordToSendJSON.put("toDate", toDate);
            recordToSendJSON.put("records", recordsAsJson);

            sendHTTPPOSTWithAuthorizationHeader(url, recordToSendJSON);
        } catch (Exception e) {
            logger.error(e);
        }
    }


    //-----------------------------------------------------------------------

    /**
     * send matchRecord, which should be linked, to SRL - In Architectur-XML Demostrator (Prozess M) step 1
     */
    public void sendMatchRecord(String url, JSONObject recordAsJson) {
        logger.info("sendMatchRecord");
        try {
            JSONObject recordToSendJSON = new JSONObject();
            JSONObject callbackObj = getCallBackURLasJSON();

            recordToSendJSON.put("callback", callbackObj);
            recordToSendJSON.put("fields", recordAsJson.get("fields"));

            sendHTTPPOSTWithAuthorizationHeader(url, recordToSendJSON);

            logger.info(recordToSendJSON.toString());
        } catch (Exception e) {
            logger.error(e);
        }
    }

    /**
     * send linkRecord, which should be linked, to SRL - In Architectur-XML (v6) step 2
     */
    //TODO: change idType and idString to map params
    public void sendMatchRecords(String url, JSONArray recordsAsJson) {
        logger.info("sendMatchRecords");
        try {
            JSONObject recordToSendJSON = new JSONObject();
            JSONObject callbackObj = getCallBackURLasJSON();

            recordToSendJSON.put("callback", callbackObj);
            recordToSendJSON.put("total", recordsAsJson.length());
            recordToSendJSON.put("toDate", toDate);
            recordToSendJSON.put("records", recordsAsJson);

            sendHTTPPOSTWithAuthorizationHeader(url, recordToSendJSON);
        } catch (Exception e) {
            logger.error(e);
        }
    }


    private JSONObject getCallBackURLasJSON() throws JSONException {
        logger.debug("getCallBackURLasJSON()");
        JSONObject callbackObj = new JSONObject();
        callbackObj.setEscapeForwardSlashAlways(false);
        callbackObj.put("url", localCallbackMatchURL);
        return callbackObj;
    }


    private JSONObject getCallBackURLasJSON(String idType, String idString) throws JSONException {
        logger.debug("getCallBackURLasJSON(" + idType + "," + idString + ")");
        JSONObject callbackObj = new JSONObject();
        callbackObj.setEscapeForwardSlashAlways(false);
        if(idString.length()==0 || idString.isEmpty()){
            callbackObj.put("url", localCallbackLinkURL + "?idType=" + idType);
        }
        if(idString.length()>0){
            callbackObj.put("url", localCallbackLinkURL + "?idType=" + idType + "&" + "idString=" + idString);
        }
        return callbackObj;
    }


    private void sendHTTPPOSTWithAuthorizationHeader(String url, JSONObject recordToSendJSON) {
        ArrayList<Header> headers = HeaderHelper.addHeaderToNewCreatedArrayList("Authorization", "apiKey apiKey=\"" + localApiKey + "\"");
        HTTPSendHelper.doRequest(url, "POST", recordToSendJSON.toString(), headers);
    }


    /**
     * rest endpoint, used to set a linked record - In Architectur-XML (v6) step 7
     */
    @POST
    @Path("/linkCallback")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response setLinkRecord(@Context HttpServletRequest req, String json) {
        logger.info("/linkCallBack called");
        logger.info("setLinkRecord()");

        if (!authorizationValidator(req)) {
            return Response.status(401).build();
        } else {
            //APIKey correct, now do the work
            try {
                JSONObject jsonObject = new JSONObject(json);
                PatientRecords pr = new PatientRecords();
                String idType = req.getParameter("idType");
                String idString = req.getParameter("idString");
                if (idString != null) {
                    JSONObject resObject = (JSONObject) jsonObject.get("result");
                    if (resObject == null) {
                        logger.error("setLinkRecord failed. " + jsonObject.get("error"));
                        return Response.status(500).build();
                    }
                    return Response.status(pr.updateRecord(idType, idString, resObject.get("linkageId").toString())).build();
                }
                else {
                    JSONArray resArray = (JSONArray) jsonObject.get("result");
                    if (resArray == null) {
                        logger.error("setLinkRecord failed. " + jsonObject.get("error"));
                        return Response.status(500).build();
                    } else {
                        for (int i = 0; i < resArray.length(); i++) {
                            JSONObject tmpObj = resArray.getJSONObject(i);
                            int status = pr.updateRecord(idType, Integer.toString(i+1), tmpObj.get("linkageId").toString());
                            if (status == 500) {
                                logger.error("setLinkRecord failed. ");
                                return Response.status(500).build();
                            }
                        }
                    }
                    return Response.status(200).build();
                }
            } catch (Exception e) {
                logger.error("setLinkRecord failed. " + e.toString());
                return Response.status(500).build();
            }
        }
    }

    /**
     * rest endpoint, used to set a linked record - In Architectur-XML (v6) step 7
     */
    @POST
    @Path("/matchCallback/{remoteID}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addMatchResult(@Context HttpServletRequest req, String json, @PathParam("remoteID") String remoteID) {
        logger.info("/matchCallBack called");
        logger.info("addMatchResult");
        logger.info("request: " + req.getQueryString());
        logger.info("json: " + json);

        if (!authorizationValidator(req)) {
            return Response.status(401).build();
        } else {
            //APIKey correct, now do the work
            try {
                JSONObject jsonObject = new JSONObject(json);
                JSONObject resObject = (JSONObject) jsonObject.get("result");
                if (resObject == null) {
                    logger.error("MatchRecord failed. " + jsonObject.get("error"));
                    return Response.status(500).build();
                } else {
                    try {
                        int matchValue = resObject.getInt("matches");
                        int tentativeMatchValue = resObject.getInt("tentativeMatches");
                        if (matchValue == 1) {
                            MatchCounter.incrementNumMatch(remoteID);
                        } else if (matchValue == 0) {
                            MatchCounter.incrementNumNonMatch(remoteID);
                        } else {
                            MatchCounter.setNumMatch(remoteID, matchValue);
                        }
                        if (tentativeMatchValue == 1) {
                            TentativeMatchCounter.incrementNumMatch(remoteID);
                        } else if (tentativeMatchValue == 0) {
                            TentativeMatchCounter.incrementNumNonMatch(remoteID);
                        } else {
                            TentativeMatchCounter.setNumMatch(remoteID, tentativeMatchValue);
                        }
                        return Response.status(200).build();
                    } catch (Exception e) {
                        logger.error("MatchRecord failed. " + e.toString());
                        return Response.status(500).build();
                    }
                }
            }
            catch (Exception e) {
                logger.error("MatchRecord failed. " + e.toString());
                return Response.status(500).build();
            }
        }
    }

    //-----------------------------------------------------------------------

    /**
     * return all entrys, which schould be compared, to SRL  - In Architectur-XML (v6) step 4
     */
    //TODO: make remoteID optional and if not set give answers back without ids
    @GET
    @Path("/getAllRecords/{remoteID}")
    //@Produces(MediaType.APPLICATION_JSON)
    public Response getAllRecords(@Context HttpServletRequest req, @Context UriInfo info, @PathParam("remoteID") String remoteID) {
        logger.info("getAllRecords()");
        if (!authorizationValidator(req)) {
            return Response.status(401).build();
        } else {
            try {
                logger.info("Query parameters: " + info.getQueryParameters());
                logger.info("Path parameters: " + remoteID);

                setQueryParameter(info.getQueryParameters().get("page"), info.getQueryParameters().get("pageSize"), info.getQueryParameters().get("toDate"));
                PatientRecords records = new PatientRecords();

                return Response.ok(prepareReturnDataSet(records.readAllPatientsAsArray(), remoteID), MediaType.APPLICATION_JSON).build();
                //return Response.ok(records.readAllPatientsAsArray(), MediaType.APPLICATION_JSON).build();
            } catch (Exception e) {
                logger.error("getAllRecords failed. " + e.toString());
                return Response.status(500).build();
            }
        }
    }

    //----Helper functions ---------------------------------------------
    private boolean authorizationValidator(HttpServletRequest request) {

        Map<String, List<String>> allowedAuthTypesAndValues = new HashMap<>();

        allowedAuthTypesAndValues.put(authenticationType, authenticationKeys);

        AuthorizationValidator authorizationValidator = new AuthorizationValidator(allowedAuthTypesAndValues);
        return authorizationValidator.validate(request);

    }

    private JSONObject prepareReturnDataSet(JSONArray records, String remoteID) throws JSONException {
        //TODO: handle page 0 and > last page

        logger.info("prepareReturnDataSet()");

        JSONObject answerObject = new JSONObject();

        JSONObject linkObject = new JSONObject();

        JSONObject selfObject = new JSONObject();
        JSONObject firstObject = new JSONObject();
        JSONObject prevObject = new JSONObject();
        JSONObject nextObject = new JSONObject();
        JSONObject lastObject = new JSONObject();

        selfObject.setEscapeForwardSlashAlways(false);
        firstObject.setEscapeForwardSlashAlways(false);
        prevObject.setEscapeForwardSlashAlways(false);
        nextObject.setEscapeForwardSlashAlways(false);
        lastObject.setEscapeForwardSlashAlways(false);

        //Create URLs for paging navigation and add to JSON
        int minPage = 1;
        int lastPage = ((int) Math.ceil((double) records.length() / (double) pageSize));
        if ((page - 1) > 0) {
            minPage = (page - 1);
        }

        selfObject.put("href", baseCommunicatorURL + "/" + remoteID);
        firstObject.put("href", baseCommunicatorURL + "/" + remoteID + "?" + "page=" + 1 + "&" + "pageSize=" + pageSize + "&" + "toDate=" + toDate);
        prevObject.put("href", baseCommunicatorURL + "/" + remoteID + "?" + "page=" + minPage + "&" + "pageSize=" + pageSize + "&" + "toDate=" + toDate);
        nextObject.put("href", baseCommunicatorURL + "/" + remoteID + "?" + "page=" + (page + 1) + "&" + "pageSize=" + pageSize + "&" + "toDate=" + toDate);
        lastObject.put("href", baseCommunicatorURL + "/" + remoteID + "?" + "page=" + lastPage + "&" + "pageSize=" + pageSize + "&" + "toDate=" + toDate);

        linkObject.setEscapeForwardSlashAlways(false);

        linkObject.put("self", selfObject);
        linkObject.put("first", firstObject);
        linkObject.put("prev", prevObject);
        linkObject.put("next", nextObject);
        linkObject.put("last", lastObject);

        answerObject.put("_links", linkObject);

        answerObject.put("total", records.length());
        answerObject.put("currentPageNumber", page);
        answerObject.put("lastPageNumber", (int) Math.ceil((double) records.length() / (double) pageSize));
        answerObject.put("pageSize", pageSize);
        answerObject.put("toDate", toDate);

        answerObject.put("localId", localId);
        answerObject.put("remoteId", remoteId);

        //Add record entrys for specific paging request
        if (page > 0 && page <= lastPage) {

            int lastReturnedEntry = (pageSize * (page - 1)) + pageSize - 1;
            int actualEntry = (pageSize * (page - 1));
            JSONArray recordsToReturn = new JSONArray();
            do {
                if (actualEntry < records.length()) {
                    recordsToReturn.put(records.getJSONObject(actualEntry));
                }
                actualEntry++;

            } while (actualEntry <= lastReturnedEntry);

            answerObject.put("records", recordsToReturn);
        }
        //not possible to use, because myArrayList is private answerObject.put("records", records.myArrayList.subList((pageSize*page),(pageSize*page)+pageSize));
        //return the whole list
        //answerObject.put("records", records);

        logger.info("send DataSet");

        return answerObject;

    }

    /**
     * Function sets Parameter which can be used to query the records
     *
     * @param newPage new value for page number.
     * @param newPageSize new value for pageSize - how many records each site max. includes
     * @param newToDate new value for toDate - maximum time of the newest entry (TODO: not implemented yet)
     */
    private void setQueryParameter(List<String> newPage, List<String> newPageSize, List<String> newToDate) {
        logger.info("setQueryParameter(): " + "newPage: " + newPage + ", newPageSize: " + newPageSize + ", newToDate" + newToDate);

        if (newPage != null) {
            if (newPage.get(0).matches("[0-9]+")) {
                logger.debug("set newPage to: " + newPage);
                page = Integer.valueOf(newPage.get(0));
            } else {
                logger.info("newPage contains not only numbers: " + newPage);
            }
        } else {
            logger.debug("pageNumber is not sent via request. Using default value: " + page);
        }

        if (newPageSize != null) {
            if (newPageSize.get(0).matches("[0-9]+")) {
                logger.debug("set newPageSize to: " + newPageSize);
                pageSize = Integer.valueOf(newPageSize.get(0));
            } else {
                logger.info("newPageSize contains not only numbers: " + newPageSize);
            }
        } else {
            logger.debug("pageSize is not sent via request. Using default value: " + page);
        }

        if (newToDate != null) {
            if (newToDate.get(0).matches("[0-9]+")) {
                logger.debug("set newToDate to: " + newToDate);
                toDate = Integer.valueOf(newToDate.get(0));
            } else {
                logger.info("newToDate contains not only numbers: " + newToDate);
            }
        } else {
            logger.debug("toDate is not sent via request. Using default value: " + toDate);
        }

    }

    //TODO: search a better place and add return http statuscode
    @GET
    @Path("/match/trigger/{remoteID}")
    public Response triggerMatch(@Context HttpServletRequest request, @PathParam("remoteID") String remoteID) throws JSONException {

        //TODO: change back to validation, if validation is necessary
        boolean test = true;

        //if (authorizationValidator(request)) {
        if (test) {
            logger.info("trigger matcher started");
            logger.info("trigger matcher " + remoteID);

            JSONObject answerObject = new JSONObject();

            //TODO: PatientRecords should use a generic interface, so we don't have to use a specific PatientRecords object here
            PatientRecords pr = new PatientRecords();
            Integer totalAmount = pr.matchPatients(remoteID);

            answerObject.put("totalAmount", totalAmount);
            MatchCounter.setNumAll(remoteID, totalAmount);
            MatchCounter.setNumMatch(remoteID, 0);
            MatchCounter.setNumNonMatch(remoteID, 0);
            TentativeMatchCounter.setNumAll(remoteID, totalAmount);
            TentativeMatchCounter.setNumMatch(remoteID, 0);
            TentativeMatchCounter.setNumNonMatch(remoteID, 0);
            return Response.ok(answerObject, MediaType.APPLICATION_JSON).build();
        } else {
            return Response.status(401).build();
        }
    }

    // Find better name
    // Triggers the first link process (M:N) for two patient list instances
    // Call only once, if repeated, the SRL IDs should be first deleted
    @GET
    @Path("/linkMN/trigger/{remoteID}")
    public Response triggerMNlink(@Context HttpServletRequest request, @PathParam("remoteID") String remoteID) throws JSONException {


        boolean test = true;

        //if (authorizationValidator(request)) {
        if (test) {
        //if (authorizationValidator(request)) {
            try {
                logger.info("trigger linker started");
                logger.info("trigger linker " + remoteID);

                JSONObject answerObject = new JSONObject();

                PatientRecords pr = new PatientRecords();
                Integer totalAmount = pr.linkPatients(remoteID, "linkRecords");

                answerObject.put("totalAmount", totalAmount);
                return Response.ok(answerObject, MediaType.APPLICATION_JSON).build();
            } catch (Exception e) {
                logger.error("SRL IDs cannot be generated: " + e.toString());
                return Response.status(500).build();
            }
        } else {
            return Response.status(401).build();
        }
    }

    // Find better name
    // Triggers the first link process (M:N) for two patient list instances
    // Call only once, if repeated, the SRL IDs should be first deleted
    @GET
    @Path("/matchMN/trigger/{remoteID}")
    public Response triggerMNmatch(@Context HttpServletRequest request, @PathParam("remoteID") String remoteID) throws JSONException {


        boolean test = true;

        //if (authorizationValidator(request)) {
        if (test) {
            //if (authorizationValidator(request)) {
            try {
                logger.info("trigger match started");
                logger.info("trigger match  " + remoteID);

                JSONObject answerObject = new JSONObject();

                PatientRecords pr = new PatientRecords();
                Integer totalAmount = pr.linkPatients(remoteID, "matchRecords");

                answerObject.put("totalAmount", totalAmount);
                logger.info(answerObject.toString());
                MatchCounter.setNumAll(remoteID, totalAmount);
                MatchCounter.setNumMatch(remoteID, 0);
                MatchCounter.setNumNonMatch(remoteID, 0);
                TentativeMatchCounter.setNumAll(remoteID, totalAmount);
                TentativeMatchCounter.setNumMatch(remoteID, 0);
                TentativeMatchCounter.setNumNonMatch(remoteID, 0);

                return Response.ok(answerObject, MediaType.APPLICATION_JSON).build();
            } catch (Exception e) {
                logger.error("Could not match the records: " + e.toString());
                return Response.status(500).build();
            }
        } else {
            return Response.status(401).build();
        }
    }

    @GET
    @Path("/match/status/{remoteID}")
    public Response triggerMatchStatus(@PathParam("remoteID") String remoteID) throws JSONException {

        logger.info("matchingStatus requested for remoteID: " + remoteID);

        if (MatchCounter.getNumAll(remoteID) == null) {
            logger.error("Wrong remoteID: " + remoteID);
            return Response.status(500).build();
        }

        JSONObject answerObject = new JSONObject();
        answerObject.put("totalAmount", MatchCounter.getNumAll(remoteID));
        answerObject.put("totalMatches", MatchCounter.getNumMatch(remoteID));
        answerObject.put("totalTentativeMatches", TentativeMatchCounter.getNumMatch(remoteID)-MatchCounter.getNumMatch(remoteID));

        logger.info("matchingStatus response: " + answerObject);

        try {
            answerObject.put("matchingStatus", "in progress");
            if (MatchCounter.getNumMatch(remoteID) + MatchCounter.getNumNonMatch(remoteID) >= MatchCounter.getNumAll(remoteID)) {
                answerObject.put("matchingStatus", "finished");
            }
            logger.info("getNumMatch:" + MatchCounter.getNumMatch(remoteID) + " getNumNonMatch: " + MatchCounter.getNumNonMatch(remoteID) + " getNumAll: " + MatchCounter.getNumAll(remoteID));

            logger.info("matchingStatus (with progress status) response: " + answerObject);
        } catch (JSONException e) {
            logger.error("matchingStatus could not be set");
            logger.error(e.getMessage());
        }

        return Response.ok(answerObject, MediaType.APPLICATION_JSON).build();
    }

    @GET
    @Path("/matchMN/status/{remoteID}")
    public Response triggerMNMatchStatus(@PathParam("remoteID") String remoteID) throws JSONException {


        logger.info("matchingStatus (M:N) requested for remoteID: " + remoteID);

        if (MatchCounter.getNumAll(remoteID) == null) {
            logger.error("Wrong remoteID: " + remoteID);
            return Response.status(500).build();
        }

        JSONObject answerObject = new JSONObject();

        answerObject.put("totalAmount", MatchCounter.getNumAll(remoteID));
        answerObject.put("totalMatches", MatchCounter.getNumMatch(remoteID));
        answerObject.put("totalTentativeMatches", TentativeMatchCounter.getNumMatch(remoteID)-MatchCounter.getNumMatch(remoteID));

        logger.info("matchingStatus (M:N) response: " + answerObject);

        try {
            answerObject.put("matchingStatus", "in progress");
            logger.info("matchingStatus (M:N) response: " + answerObject);
        } catch (JSONException e) {
            logger.error("matchingStatus (M:N) could not be set");
            logger.error(e.getMessage());
        }

        return Response.ok(answerObject, MediaType.APPLICATION_JSON).build();
    }

    @GET
    @Path("/initIDs/{remoteID}")
    public Response generateIDs(@Context HttpServletRequest request, @PathParam("remoteID") String remoteID) throws JSONException {
//        if (authorizationValidator(request)) {
        try {
            logger.info("trigger linker started");
            logger.info("trigger linker " + remoteID);

            JSONObject answerObject = new JSONObject();

            PatientRecords pr = new PatientRecords();
            Integer totalAmount  = pr.getCount();

            logger.info("total amount: " + totalAmount);

            String url = baseLinkageServiceURL + "/freshIDs/" + localId + "?count=" + totalAmount;

            CloseableHttpResponse result = HTTPSendHelper.doRequest(url, "GET", null );
            if (result.getStatusLine().getStatusCode() > 200) {
                return Response.status(result.getStatusLine().getStatusCode()).build();
            }

            HttpEntity entity = result.getEntity();
            if (entity != null) {
                String entityString = EntityUtils.toString(entity,"UTF-8");
                JSONObject tmpObj = new JSONObject(entityString);
                JSONArray resArray = (JSONArray)tmpObj.get("linkageIds");
                String idType = "link-"+localId+"-"+remoteID;
                int status = pr.updateRecords(idType, resArray);
                if (status > 200) {
                    return Response.status(status).build();
                }
            }

            answerObject.put("totalAmount", totalAmount);
            return Response.ok(answerObject, MediaType.APPLICATION_JSON).build();
        } catch (Exception e) {
            logger.error("SRL IDs cannot be generated: " + e.toString());
            return Response.status(500).build();
        }
//        } else {
//        return Response.status(401).build();
//        }
    }


}
