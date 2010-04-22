package org.collectionspace.chain.csp.persistence.services.user;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.collectionspace.chain.csp.persistence.services.XmlJsonConversion;
import org.collectionspace.chain.csp.persistence.services.connection.ConnectionException;
import org.collectionspace.chain.csp.persistence.services.connection.RequestMethod;
import org.collectionspace.chain.csp.persistence.services.connection.ReturnedDocument;
import org.collectionspace.chain.csp.persistence.services.connection.ReturnedMultipartDocument;
import org.collectionspace.chain.csp.persistence.services.connection.ReturnedURL;
import org.collectionspace.chain.csp.persistence.services.connection.ServicesConnection;
import org.collectionspace.chain.csp.persistence.services.vocab.URNProcessor;
import org.collectionspace.chain.csp.schema.Field;
import org.collectionspace.chain.csp.schema.FieldSet;
import org.collectionspace.chain.csp.schema.Record;
import org.collectionspace.csp.api.core.CSPRequestCache;
import org.collectionspace.csp.api.core.CSPRequestCredentials;
import org.collectionspace.csp.api.persistence.ExistException;
import org.collectionspace.csp.api.persistence.Storage;
import org.collectionspace.csp.api.persistence.UnderlyingStorageException;
import org.collectionspace.csp.api.persistence.UnimplementedException;
import org.collectionspace.csp.helper.persistence.ContextualisedStorage;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;

import org.dom4j.Node;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;

/** Implements user service connection over service layer. Generally simply a much simpler version of regular record storage, but also uses non-multiparts.
 * 
 * @author dan
 *
 */

public class UserStorage implements ContextualisedStorage {
	private static final Logger log=LoggerFactory.getLogger(UserStorage.class);
	private ServicesConnection conn;
	private Record r;
	private Map<String,String> view_good=new HashMap<String,String>();
	private Map<String,String> view_map=new HashMap<String,String>();
	private Set<String> xxx_view_deurn=new HashSet<String>();

	public UserStorage(Record r,ServicesConnection conn) throws DocumentException, IOException {	
		this.conn=conn;
		this.r=r;
	}

	private void setGleanedValue(CSPRequestCache cache,String path,String key,String value) {
		cache.setCached(getClass(),new String[]{"glean",path,key},value);
	}

	private String getGleanedValue(CSPRequestCache cache,String path,String key) {
		return (String)cache.getCached(getClass(),new String[]{"glean",path,key});
	}

	private JSONObject correctPassword(JSONObject in) throws JSONException, UnderlyingStorageException {
		try {
			if(in.has("password")){
				String password=in.getString("password");
				in.remove("password");
				password=Base64.encode(password.getBytes("UTF-8"));
				while(password.endsWith("\n") || password.endsWith("\r"))
					password=password.substring(0,password.length()-1);
				in.put("password",password);
			}
			return in;
		} catch (UnsupportedEncodingException e) {
			throw new UnderlyingStorageException("Error generating Base 64",e);
		}
	}
	
	/* XXX FIXME  in here until we fix the UI layer to pass the data correctly */
	private JSONObject correctScreenName(JSONObject in) throws JSONException, UnderlyingStorageException {
		if(in.has("userName") && !in.has("screenName")){
			String username=in.getString("userName");
			in.remove("userName");
			in.put("screenName",username);
		}
		return in;
	}
	
	/* XXX FIXME in here until we fix the UI layer to pass the data correctly */
	private JSONObject correctUserId(JSONObject in) throws JSONException, UnderlyingStorageException {
		if(!in.has("userId")){
			String userId=in.getString("email");
			in.remove("userId");
			in.put("userId",userId);
		}
		return in;
	}
	
	public String autocreateJSON(ContextualisedStorage root,CSPRequestCredentials creds,CSPRequestCache cache,String filePath, JSONObject jsonObject) throws ExistException, UnimplementedException, UnderlyingStorageException {
		try {
			jsonObject=correctPassword(jsonObject);
			jsonObject= correctScreenName(jsonObject);
			jsonObject= correctUserId(jsonObject);
			Document doc=XmlJsonConversion.convertToXml(r,jsonObject,"common");
			ReturnedURL url = conn.getURL(RequestMethod.POST,r.getServicesURL()+"/",doc,creds,cache);
			if(url.getStatus()>299 || url.getStatus()<200)
				throw new UnderlyingStorageException("Bad response "+url.getStatus());
			return url.getURLTail();
		} catch (ConnectionException e) {
			throw new UnderlyingStorageException("Service layer exception",e);
		} catch (JSONException e) {
			throw new UnimplementedException("JSONException",e);
		}
	}

	public void createJSON(ContextualisedStorage root,CSPRequestCredentials creds,CSPRequestCache cache,String filePath, JSONObject jsonObject)
	throws ExistException, UnimplementedException, UnderlyingStorageException {
		throw new UnimplementedException("Cannot post to full path");
	}

	public void deleteJSON(ContextualisedStorage root,CSPRequestCredentials creds,CSPRequestCache cache,String filePath) throws ExistException,
	UnimplementedException, UnderlyingStorageException {
		try {
			int status=conn.getNone(RequestMethod.DELETE,r.getServicesURL()+"/"+filePath,null,creds,cache);
			if(status>299 || status<200) // XXX CSPACE-73, should be 404
				throw new UnderlyingStorageException("Service layer exception status="+status);
		} catch (ConnectionException e) {
			throw new UnderlyingStorageException("Service layer exception",e);
		}		
	}

	@SuppressWarnings("unchecked")
	public String[] getPaths(ContextualisedStorage root,CSPRequestCredentials creds,CSPRequestCache cache,String rootPath,JSONObject restrictions) throws ExistException, UnimplementedException, UnderlyingStorageException {
		try {
			List<String> out=new ArrayList<String>();
			Iterator rit=restrictions.keys();
			StringBuffer args=new StringBuffer();
			while(rit.hasNext()) {
				String key=(String)rit.next();
				FieldSet fs=r.getField(key);
				if(!(fs instanceof Field))
					continue;
				String filter=((Field)fs).getServicesFilterParam();
				if(filter==null)
					continue;
				args.append('&');
				args.append(filter);
				args.append('=');
				args.append(URLEncoder.encode(restrictions.getString(key),"UTF-8"));
			}
			String tail=args.toString();
			if(tail.length()>0)
				tail="?"+tail.substring(1);
			ReturnedDocument doc=conn.getXMLDocument(RequestMethod.GET,r.getServicesURL()+"/"+tail,null,creds,cache);
			if(doc.getStatus()<200 || doc.getStatus()>399)
				throw new UnderlyingStorageException("Cannot retrieve account list");
			Document list=doc.getDocument();
			List<Node> objects=list.selectNodes(r.getServicesListPath());
			for(Node object : objects) {
				List<Node> fields=object.selectNodes("*");
				String csid=object.selectSingleNode("csid").getText();
				for(Node field : fields) {
					if("csid".equals(field.getName())) {
						int idx=csid.lastIndexOf("/");
						if(idx!=-1)
							csid=csid.substring(idx+1);
						out.add(csid);						
					} else if("uri".equals(field.getName())) {
						// Skip!
					} else {
						String json_name=view_map.get(field.getName());
						if(json_name!=null) {
							String value=field.getText();
							// XXX hack to cope with multi values		
							if(value==null || "".equals(value)) {
								List<Node> inners=field.selectNodes("*");
								for(Node n : inners) {
									value+=n.getText();
								}
							}
							setGleanedValue(cache,r.getServicesURL()+"/"+csid,json_name,value);
						}
					}
				}
			}
			return out.toArray(new String[0]);
		} catch (ConnectionException e) {
			throw new UnderlyingStorageException("Service layer exception",e);
		} catch (UnsupportedEncodingException e) {
			throw new UnderlyingStorageException("Exception building query",e);
		} catch (JSONException e) {
			throw new UnderlyingStorageException("Exception building query",e);
		}
	}

	// XXX support URNs for reference
	private JSONObject miniForURI(ContextualisedStorage storage,CSPRequestCredentials creds,CSPRequestCache cache,String refname,String uri) throws ExistException, UnimplementedException, UnderlyingStorageException, JSONException {
		return storage.retrieveJSON(storage,creds,cache,"direct/urn/"+uri+"/"+refname);
	}
	
	public JSONObject refViewRetrieveJSON(ContextualisedStorage storage,CSPRequestCredentials creds,CSPRequestCache cache,String filePath) throws ExistException,UnimplementedException, UnderlyingStorageException, JSONException {
		try {
			ReturnedDocument all = conn.getXMLDocument(RequestMethod.GET,r.getServicesURL()+"/"+filePath+"/authorityrefs",null,creds,cache);
			if(all.getStatus()!=200)
				throw new ConnectionException("Bad request during identifier cache map update: status not 200");
			Document list=all.getDocument();
			JSONObject out=new JSONObject();
			for(Object node : list.selectNodes("authority-ref-list/authority-ref-item")) {
				if(!(node instanceof Element))
					continue;
				String key=((Element)node).selectSingleNode("sourceField").getText();
				String uri=((Element)node).selectSingleNode("uri").getText();
				String refname=((Element)node).selectSingleNode("refName").getText();
				if(uri!=null && uri.startsWith("/"))
					uri=uri.substring(1);
				JSONObject data=miniForURI(storage,creds,cache,refname,uri);
				out.put(key,data);
			}
			return out;
		} catch (ConnectionException e) {
			throw new UnderlyingStorageException("Connection problem",e);
		}
	}

	public JSONObject retrieveJSON(ContextualisedStorage root,CSPRequestCredentials creds,CSPRequestCache cache,String filePath) throws ExistException,
	UnimplementedException, UnderlyingStorageException {
		try {
			ReturnedDocument doc = conn.getXMLDocument(RequestMethod.GET,r.getServicesURL()+"/"+filePath,null,creds,cache);
			JSONObject out=new JSONObject();
			if((doc.getStatus()<200 || doc.getStatus()>=300))
				throw new ExistException("Does not exist "+filePath);
			out=XmlJsonConversion.convertToJson(r,doc.getDocument());
			return out;
		} catch (ConnectionException e) {
			throw new UnderlyingStorageException("Service layer exception",e);
		} catch (JSONException e) {
			throw new UnderlyingStorageException("Service layer exception",e);
		}
	}

	private JSONObject xxx_cspace1458_fix(String filePath,JSONObject in,CSPRequestCredentials creds,CSPRequestCache cache) throws ConnectionException, UnderlyingStorageException, JSONException {
		// We need to also specify status and createdAt (revealed by GET) because of CSPACE-1458.
		ReturnedDocument doc = conn.getXMLDocument(RequestMethod.GET,r.getServicesURL()+"/"+filePath,null,creds,cache);
		if(doc.getStatus()>299 || doc.getStatus()<200)
			throw new UnderlyingStorageException("Bad response "+doc.getStatus());
		System.err.println(doc.getDocument().asXML());
		String created_at=doc.getDocument().selectSingleNode("accounts_common/createdAt").getText();
		String status=doc.getDocument().selectSingleNode("accounts_common/status").getText();		
		in.put("createdAt",created_at);
		if(!in.has("status"))
			in.put("status",status);
		//in.remove("password");
		return in;
	}
	
	public void updateJSON(ContextualisedStorage root,CSPRequestCredentials creds,CSPRequestCache cache,String filePath, JSONObject jsonObject)
	throws ExistException, UnimplementedException, UnderlyingStorageException {
		try {
			// XXX when CSPACE-1458 is fixed, remove the call to xxx_cspace1458_fix, and just pass jsonObject as this arg. (fao Chris or somoeone else at CARET).
			jsonObject=correctPassword(jsonObject);
			Document in=XmlJsonConversion.convertToXml(r,xxx_cspace1458_fix(filePath,jsonObject,creds,cache),"common");
			System.err.println("Sending: "+in.asXML());
			ReturnedDocument doc = conn.getXMLDocument(RequestMethod.PUT,r.getServicesURL()+"/"+filePath,in,creds,cache);
			if(doc.getStatus()==404)
				throw new ExistException("Not found: "+r.getServicesURL()+"/"+filePath);
			if(doc.getStatus()>299 || doc.getStatus()<200)
				throw new UnderlyingStorageException("Bad response "+doc.getStatus());
		} catch (ConnectionException e) {
			throw new UnderlyingStorageException("Service layer exception",e);
		} catch (JSONException e) {
			throw new UnimplementedException("JSONException",e);
		}
	}
}