package com.rsicms.rsuite.translation.webservice;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.reallysi.rsuite.api.Alias;
import com.reallysi.rsuite.api.ManagedObject;
import com.reallysi.rsuite.api.MetaDataItem;
import com.reallysi.rsuite.api.ObjectType;
import com.reallysi.rsuite.api.RSuiteException;
import com.reallysi.rsuite.api.User;
import com.reallysi.rsuite.api.control.ObjectCopyOptions;
import com.reallysi.rsuite.api.remoteapi.CallArgument;
import com.reallysi.rsuite.api.remoteapi.CallArgumentList;
import com.reallysi.rsuite.api.remoteapi.ContextPath;
import com.reallysi.rsuite.api.remoteapi.DefaultRemoteApiHandler;
import com.reallysi.rsuite.api.remoteapi.RemoteApiExecutionContext;
import com.reallysi.rsuite.api.remoteapi.RemoteApiResult;
import com.reallysi.rsuite.api.remoteapi.result.MessageDialogResult;
import com.reallysi.rsuite.api.remoteapi.result.MessageType;
import com.reallysi.rsuite.api.remoteapi.result.NotificationAction;
import com.reallysi.rsuite.api.remoteapi.result.RestResult;
import com.reallysi.rsuite.api.remoteapi.result.UserInterfaceAction;
import com.rsicms.rsuite.helpers.utils.RSuiteUtils;
import com.rsicms.rsuite.translation.TranslationConstants;

public class CopyForTranslation extends DefaultRemoteApiHandler {

	private static Log log = LogFactory.getLog(CopyForTranslation.class);

	@Override
	public RemoteApiResult execute(RemoteApiExecutionContext context, CallArgumentList args) throws RSuiteException  {

		for (CallArgument arg : args.getAll()) {
			log.info("Arg: " + arg.getName() + ":" + arg.getValue());
		}

		User user = context.getSession().getUser();
		
		String baseMoid = args.getFirstString("baseMoid");

		List<String> refreshIds = new ArrayList<String>();
		
		ManagedObject sourceMo = context.getManagedObjectService().getManagedObject(user, baseMoid);
		
		//TODO extract this code to utils so can reuse here and in other translation request actions
		
		List<String> langs = args.getStrings("lang");
		for (String lang : langs) {
			String query = "/*[rmd:get-lmd-value(., '" + TranslationConstants.LMD_TRANSLATION_OF + "')='" + baseMoid + 
					"' and rmd:get-lmd-value(., '" + TranslationConstants.LMD_LANGUAGE + "')='" + lang + "']";
			ManagedObject transMo = null;
			try {
				List<ManagedObject> transMos = context.getSearchService().executeXPathSearch(user, query, 1, 1);
				MetaDataItem baseLangLmd =  new MetaDataItem(TranslationConstants.LMD_IS_BASE_LANGUAGE, "false"); 
				MetaDataItem transOfLmd =  new MetaDataItem(TranslationConstants.LMD_TRANSLATION_OF, baseMoid); 
				MetaDataItem transOfRevLmd =  new MetaDataItem(TranslationConstants.LMD_TRANSLATION_OF_REVISION, sourceMo.getVersionHistory().getCurrentVersionEntry().getRevisionNumber());
				if (transMos.size() == 0) {

	            	ObjectCopyOptions options = new ObjectCopyOptions();
					options.setDisplayName("COPY OF " + sourceMo.getId() + " FOR TRANSLATION (" + lang + ")");
					Alias[] aliases = sourceMo.getAliases();
					for (int i = 0; i < aliases.length; i++) {
						aliases[i] = new Alias(aliases[i].getType(), lang + aliases[i].getText());
					}
					options.setAliases(aliases);
	                
					transMo = context.getManagedObjectService().copy(user, baseMoid, options );
					MetaDataItem langLmd =  new MetaDataItem(TranslationConstants.LMD_LANGUAGE, lang);
					context.getManagedObjectService().setMetaDataEntry(user, transMo.getId(), baseLangLmd);
					context.getManagedObjectService().setMetaDataEntry(user, transMo.getId(), transOfLmd);
					context.getManagedObjectService().setMetaDataEntry(user, transMo.getId(), transOfRevLmd);
					context.getManagedObjectService().setMetaDataEntry(user, transMo.getId(), langLmd);

	                try {
	                	ContextPath cp = ContextPath.fromString(args.getFirstString("rsuiteBrowseUri"), user, context);
	                	ManagedObject caMo = null;
	                	for (int i = 0; i < cp.size(); i++) {
	                		caMo = RSuiteUtils.getRealMo(context, user, context.getManagedObjectService().getManagedObject(user, cp.get(cp.size() - (i+1)).getId()));
	                		if (caMo.getObjectType().equals(ObjectType.CONTENT_ASSEMBLY)) {
	    						context.getContentAssemblyService().attach(user, caMo.getId(), transMo.getId(), null);
	    						refreshIds.add(caMo.getId());
	    						break;
	                		}
	                	}
	                } catch (Exception e1) {
	                	log.error("ca issue" + e1);
	                }
					refreshIds.add(transMo.getId());
				} else {
					transMo = transMos.get(0);
					context.getManagedObjectService().setMetaDataEntry(user, transMo.getId(), baseLangLmd);
					context.getManagedObjectService().setMetaDataEntry(user, transMo.getId(), transOfLmd);
					context.getManagedObjectService().setMetaDataEntry(user, transMo.getId(), transOfRevLmd);
					refreshIds.add(transMo.getId());
				}
			} catch (RSuiteException e) {
				log.error("Error during translation addition: " + e.getMessage() + e);
				return new MessageDialogResult(MessageType.ERROR, "Translation Addition Error",
						"Error during translation addition. If this persists, see your system administrator.");
			}

			//TODO end reusable code block
			
			MetaDataItem transStateLmd =  new MetaDataItem(TranslationConstants.LMD_TRANSLATION_STATE, TranslationConstants.STATUS_NOT_TRANSLATED_VALUE);
			context.getManagedObjectService().setMetaDataEntry(user, transMo.getId(), transStateLmd );

		}

		NotificationAction notificationAction = new NotificationAction("Translations have been added." );
        RestResult webServiceResult = new RestResult();
        webServiceResult.addAction(notificationAction);
        UserInterfaceAction uiAction = new UserInterfaceAction("rsuite:refreshManagedObjects");
        StringBuffer sb = new StringBuffer();
        for (String id : refreshIds) {
        	sb.append(id + ",");
        }
        uiAction.addProperty("objects", sb.toString());
        uiAction.addProperty("children", true);
        webServiceResult.addAction(uiAction);
        return webServiceResult;
	}

}
