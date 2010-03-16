package org.collectionspace.chain.storage;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.collectionspace.chain.controller.ChainServlet;
import org.collectionspace.chain.util.json.JSONUtils;
import org.collectionspace.bconfigutils.bootstrap.BootstrapConfigController;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.mortbay.jetty.testing.ServletTester;

public class TestServiceThroughWebapp {
	// XXX refactor
	private InputStream getResource(String name) {
		String path=getClass().getPackage().getName().replaceAll("\\.","/")+"/"+name;
		return Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
	}

	// XXX refactor
	private String getResourceString(String name) throws IOException {
		InputStream in=getResource(name);
		return IOUtils.toString(in,"UTF-8");
	}
	
	// XXX refactor
	private UTF8SafeHttpTester jettyDo(ServletTester tester,String method,String path,String data_str) throws IOException, Exception {
		UTF8SafeHttpTester out=new UTF8SafeHttpTester();
		out.request(tester,method,path,data_str);
		return out;
	}
	
	// XXX refactor into other copy of this method
	private ServletTester setupJetty() throws Exception {
		BootstrapConfigController config_controller=new BootstrapConfigController(null);
		config_controller.addSearchSuffix("test-config-loader2.xml");
		config_controller.go();
		String base=config_controller.getOption("services-url");		
		ServletTester tester=new ServletTester();
		tester.setContextPath("/chain");
		tester.addServlet(ChainServlet.class, "/*");
		tester.addServlet("org.mortbay.jetty.servlet.DefaultServlet", "/");
		tester.setAttribute("storage","service");
		tester.setAttribute("store-url",base+"/cspace-services/");	
		tester.setAttribute("config-filename","default.xml");
		tester.start();
		return tester;
	}
	
	private JSONObject getFields(JSONObject in) throws JSONException {
		in=in.getJSONObject("fields");
		in.remove("csid");
		return in;
	}
	
	private JSONObject makeRequest(JSONObject fields) throws JSONException {
		JSONObject out=new JSONObject();
		out.put("fields",fields);
		return out;
	}
	
	private String makeSimpleRequest(String in) throws JSONException {
		return makeRequest(new JSONObject(in)).toString();
	}
	
	@Test public void testCollectionObjectBasic() throws Exception {
		ServletTester jetty=setupJetty();
		UTF8SafeHttpTester out=jettyDo(jetty,"POST","/chain/objects/",makeSimpleRequest(getResourceString("obj3.json")));	
		String id=out.getHeader("Location");
		assertEquals(201,out.getStatus());
		out=jettyDo(jetty,"GET","/chain"+id,null);
		JSONObject content=new JSONObject(out.getContent());
		content=getFields(content);
		assertTrue(JSONUtils.checkJSONEquivOrEmptyStringKey(new JSONObject(getResourceString("obj3.json")),content));
		out=jettyDo(jetty,"PUT","/chain"+id,makeSimpleRequest(getResourceString("obj4.json")));
		assertEquals(200,out.getStatus());
		out=jettyDo(jetty,"GET","/chain"+id,null);
		content=new JSONObject(out.getContent());
		content=getFields(content);
		assertTrue(JSONUtils.checkJSONEquivOrEmptyStringKey(new JSONObject(getResourceString("obj4.json")),content));		
		out=jettyDo(jetty,"DELETE","/chain"+id,null);
		out=jettyDo(jetty,"GET","/chain"+id,null);
		assertTrue(out.getStatus()!=200); // XXX should be 404
	}

	@Test public void testIntake() throws Exception {
		ServletTester jetty=setupJetty();
		UTF8SafeHttpTester out=jettyDo(jetty,"POST","/chain/intake/",makeSimpleRequest(getResourceString("int3.json")));	
		assertEquals(201,out.getStatus());
		String path=out.getHeader("Location");
		out=jettyDo(jetty,"GET","/chain"+path,null);
		System.err.println(out.getContent());
		JSONObject content=new JSONObject(out.getContent());
		content=getFields(content);
		assertTrue(JSONUtils.checkJSONEquivOrEmptyStringKey(new JSONObject(getResourceString("int3.json")),content));
		out=jettyDo(jetty,"PUT","/chain"+path,makeSimpleRequest(getResourceString("int4.json")));
		assertEquals(200,out.getStatus());
		out=jettyDo(jetty,"GET","/chain"+path,null);
		content=new JSONObject(out.getContent());
		content=getFields(content);
		assertTrue(JSONUtils.checkJSONEquivOrEmptyStringKey(new JSONObject(getResourceString("int4.json")),content));		
		out=jettyDo(jetty,"DELETE","/chain"+path,null);
		out=jettyDo(jetty,"GET","/chain"+path,null);
		assertTrue(out.getStatus()!=200); // XXX should be 404		
	}

	@Test public void testAcquisition() throws Exception {
		ServletTester jetty=setupJetty();
		UTF8SafeHttpTester out=jettyDo(jetty,"POST","/chain/acquisition/",makeSimpleRequest(getResourceString("int5.json")));	
		assertEquals(201,out.getStatus());
		String path=out.getHeader("Location");
		out=jettyDo(jetty,"GET","/chain"+path,null);
		System.err.println(out.getContent());
		JSONObject content=new JSONObject(out.getContent());
		content=getFields(content);
		assertTrue(JSONUtils.checkJSONEquivOrEmptyStringKey(new JSONObject(getResourceString("int5.json")),content));
		out=jettyDo(jetty,"PUT","/chain"+path,makeSimpleRequest(getResourceString("int6.json")));
		assertEquals(200,out.getStatus());
		out=jettyDo(jetty,"GET","/chain"+path,null);
		content=new JSONObject(out.getContent());
		content=getFields(content);
		assertTrue(JSONUtils.checkJSONEquivOrEmptyStringKey(new JSONObject(getResourceString("int6.json")),content));		
		out=jettyDo(jetty,"DELETE","/chain"+path,null);
		out=jettyDo(jetty,"GET","/chain"+path,null);
		assertTrue(out.getStatus()!=200); // XXX should be 404		
	}
	
	@Test public void testIDGenerate() throws Exception {
		ServletTester jetty=setupJetty();
		UTF8SafeHttpTester out=jettyDo(jetty,"GET","/chain/id/intake",null);
		JSONObject jo=new JSONObject(out.getContent());
		assertTrue(jo.getString("next").startsWith("IN2010."));
		out=jettyDo(jetty,"GET","/chain/id/objects",null);
		jo=new JSONObject(out.getContent());
		assertTrue(jo.getString("next").startsWith("2010.1."));
	}

	@Test public void testAutoGet() throws Exception {
		ServletTester jetty=setupJetty();
		UTF8SafeHttpTester out=jettyDo(jetty,"GET","/chain/objects/__auto",null);
		assertEquals(200,out.getStatus());
		// XXX this is correct currently, whilst __auto is stubbed.
		assertTrue(JSONUtils.checkJSONEquivOrEmptyStringKey(new JSONObject(),new JSONObject(out.getContent())));
	}
	
	@Test public void testList() throws Exception {
		ServletTester jetty=setupJetty();
		// delete all
		UTF8SafeHttpTester out=jettyDo(jetty,"GET","/chain/objects",null);
		assertEquals(200,out.getStatus());
		JSONObject in=new JSONObject(out.getContent());
		JSONArray items=in.getJSONArray("items");
		for(int i=0;i<items.length();i++) {
			JSONObject data=items.getJSONObject(i);
			out=jettyDo(jetty,"DELETE","/chain/objects/"+data.getString("csid"),null);
			assertEquals(200,out.getStatus());
		}
		// empty
		out=jettyDo(jetty,"GET","/chain/objects",null);
		assertEquals(200,out.getStatus());
		in=new JSONObject(out.getContent());
		items=in.getJSONArray("items");
		assertEquals(0,items.length());
		// put a couple in
		out=jettyDo(jetty,"POST","/chain/objects/",makeSimpleRequest(getResourceString("obj3.json")));	
		String id1=out.getHeader("Location");
		assertEquals(201,out.getStatus());
		out=jettyDo(jetty,"POST","/chain/objects/",makeSimpleRequest(getResourceString("obj3.json")));	
		String id2=out.getHeader("Location");
		assertEquals(201,out.getStatus());
		// size 2, right ones, put them in the right place
		out=jettyDo(jetty,"GET","/chain/objects",null);
		assertEquals(200,out.getStatus());
		in=new JSONObject(out.getContent());
		items=in.getJSONArray("items");
		assertEquals(2,items.length());
		JSONObject obj1=items.getJSONObject(0);
		JSONObject obj2=items.getJSONObject(1);
		if(id2.split("/")[2].equals(obj1.getString("csid"))) {
			JSONObject t=obj1;
			obj1=obj2;
			obj2=t;
		}
		// check
		assertEquals(id1.split("/")[2],obj1.getString("csid"));
		assertEquals(id2.split("/")[2],obj2.getString("csid"));
		assertEquals("objects",obj1.getString("recordtype"));
		assertEquals("objects",obj2.getString("recordtype"));
		assertEquals("title",obj1.getString("summary"));
		assertEquals("title",obj2.getString("summary"));
		assertEquals("objectNumber",obj1.getString("number"));
		assertEquals("objectNumber",obj2.getString("number"));
	}
	
	@Test public void testSearch() throws Exception {
		ServletTester jetty=setupJetty();
		// one aardvark, one non-aardvark
		UTF8SafeHttpTester out=jettyDo(jetty,"POST","/chain/objects/",makeSimpleRequest(getResourceString("obj3-search.json")));	
		String good=out.getHeader("Location").split("/")[2];
		assertEquals(201,out.getStatus());
		out=jettyDo(jetty,"POST","/chain/objects/",makeSimpleRequest(getResourceString("obj3.json")));	
		String bad=out.getHeader("Location").split("/")[2];
		assertEquals(201,out.getStatus());
		// search
		out=jettyDo(jetty,"GET","/chain/objects/search?query=aardvark",null);
		assertEquals(200,out.getStatus());
		System.err.println(out.getContent());
		// check
		JSONArray results=new JSONObject(out.getContent()).getJSONArray("results");
		boolean found=false;
		for(int i=0;i<results.length();i++) {
			String csid=results.getJSONObject(i).getString("csid");
			if(good.equals(csid))
				found=true;
			if(bad.equals(csid))
				assertTrue(false);
		}
		assertTrue(found);
	}
}