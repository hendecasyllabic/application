package org.collectionspace.chain.csp.persistence.services.vocab;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.collectionspace.chain.csp.persistence.services.connection.ConnectionException;
import org.collectionspace.chain.csp.persistence.services.connection.RequestMethod;
import org.collectionspace.chain.csp.persistence.services.connection.ReturnedDocument;
import org.collectionspace.chain.csp.persistence.services.connection.ReturnedURL;
import org.collectionspace.chain.csp.persistence.services.connection.ServicesConnection;
import org.collectionspace.chain.csp.schema.Record;
import org.collectionspace.csp.api.core.CSPRequestCache;
import org.collectionspace.csp.api.persistence.ExistException;
import org.collectionspace.csp.api.persistence.UnderlyingStorageException;
import org.dom4j.Document;
import org.dom4j.DocumentFactory;
import org.dom4j.Element;
import org.dom4j.Node;

public class VocabInstanceCache {
	private Map<String,String> csids=new HashMap<String,String>();
	private ServicesConnection conn;
	private Map<String,String> vocabs;
	private Record r;
	
	VocabInstanceCache(Record r,ServicesConnection conn,Map<String,String> vocabs) {
		this.conn=conn;
		this.vocabs=vocabs;
		this.r=r;
	}
	
	/* confound and unconfound extract and add the trailing identifier in the displayName which is used to sync with the config */
	private String unconfound(String name) {
		if(name==null)
			return null;
		if(!name.endsWith(")"))
			return null;
		int pos=name.lastIndexOf('(');
		if(pos==-1)
			return null;
		String rest=name.substring(pos+1);
		return rest.substring(0,rest.length()-1);
	}
	
	private String confound(String name) throws ExistException {
		if(!vocabs.containsKey(name))
			throw new ExistException("No such vocab "+name);
		return vocabs.get(name)+" ("+name+")";
	}
	
	private Document createList(String namespace,String tag,String id) throws ExistException {
		Document out=DocumentFactory.getInstance().createDocument();
		String[] path=tag.split("/");
		Element root=out.addElement("ns2:"+path[0],namespace);
		for(int i=1;i<path.length;i++) {
			root=root.addElement(path[i]);
		}
		Element nametag=root.addElement("displayName");
		nametag.addText(confound(id));
		Element vocabtag=root.addElement("vocabType");
		vocabtag.addText("OrgAuthority"); // XXX
		return out;
	}
	
	// Only called if doesn't exist
	private synchronized void createVocabulary(CSPRequestCache cache,String id) throws ConnectionException, UnderlyingStorageException, ExistException {
		Map<String,Document> body=new HashMap<String,Document>();
		String[] path_parts=r.getServicesSingleInstancePath().split(":",2);
		String[] tag_parts=path_parts[1].split(",",2);
		body.put(path_parts[0],createList(tag_parts[0],tag_parts[1],id));
		ReturnedURL out=conn.getMultipartURL(RequestMethod.POST,"/"+r.getServicesURL()+"/",body);
		if(out.getStatus()>299)
			throw new UnderlyingStorageException("Could not create vocabulary status="+out.getStatus());
		csids.put(id,out.getURLTail());
	}
	
	@SuppressWarnings("unchecked")
	private void buildVocabularies(CSPRequestCache cache) throws ConnectionException, UnderlyingStorageException {
		ReturnedDocument data=conn.getXMLDocument(RequestMethod.GET,"/"+r.getServicesURL()+"/",null);
		Document doc=data.getDocument();
		if(doc==null)
			throw new UnderlyingStorageException("Could not retrieve vocabularies");		
		String[] path_parts=r.getServicesInstancesPath().split(":",2);
		String[] tag_parts=path_parts[1].split(",",2);
		List<Node> objects=doc.getDocument().selectNodes(tag_parts[1]);
		for(Node object : objects) {
			String name=object.selectSingleNode("displayName").getText();
			String base=unconfound(name);
			if(base==null)
				continue;
			if(!vocabs.containsKey(base))
				continue;
			csids.put(base,object.selectSingleNode("csid").getText());
		}
	}
	
	String getVocabularyId(CSPRequestCache cache,String id) throws ConnectionException, UnderlyingStorageException, ExistException {
		if(csids.containsKey(id))
			return csids.get(id);
		synchronized(getClass()) {
			buildVocabularies(cache);
			if(csids.containsKey(id))
				return csids.get(id);
			createVocabulary(cache,id);
			if(csids.containsKey(id))
				return csids.get(id);
			throw new UnderlyingStorageException("Bad vocabulary "+id);
		}
	}
}