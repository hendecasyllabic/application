package org.collectionspace.chain.csp.webui.authorities;

import org.apache.commons.lang.StringUtils;
import org.collectionspace.chain.csp.schema.Instance;
import org.collectionspace.chain.csp.schema.Spec;
import org.collectionspace.chain.csp.webui.main.Request;
import org.collectionspace.chain.csp.webui.main.WebMethod;
import org.collectionspace.chain.csp.webui.main.WebUI;
import org.collectionspace.csp.api.persistence.ExistException;
import org.collectionspace.csp.api.persistence.Storage;
import org.collectionspace.csp.api.persistence.UnderlyingStorageException;
import org.collectionspace.csp.api.persistence.UnimplementedException;
import org.collectionspace.csp.api.ui.UIException;
import org.collectionspace.csp.api.ui.UIRequest;

public class VocabulariesDelete implements WebMethod {
	private Instance n;
	
	public VocabulariesDelete(Instance n) {
		this.n=n;
	}

	public void configure(WebUI ui, Spec spec) {}

	private void store_delete(Storage storage,UIRequest request,String path) throws UIException {
		try {
			storage.deleteJSON(n.getRecord().getID()+"/"+n.getTitleRef()+"/"+path);
		} catch (ExistException e) {
			throw new UIException("JSON Not found "+e,e);
		} catch (UnimplementedException e) {
			throw new UIException("Unimplemented",e);
		} catch (UnderlyingStorageException e) {
			throw new UIException("Problem getting",e);
		}
	}
	
	public void run(Object in, String[] tail) throws UIException {
		Request q=(Request)in;
		store_delete(q.getStorage(),q.getUIRequest(),StringUtils.join(tail,"/"));
	}

}