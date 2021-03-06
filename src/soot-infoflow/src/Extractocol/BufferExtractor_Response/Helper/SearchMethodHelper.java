
package Extractocol.BufferExtractor_Response.Helper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import soot.SootMethod;
import soot.Unit;
import soot.jimple.AssignStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InterfaceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.StaticInvokeExpr;
import soot.jimple.VirtualInvokeExpr;
import Extractocol.Constants;
import Extractocol.BufferExtractor_Request.Basics.BFNode;
import Extractocol.BufferExtractor_Request.Basics.ContextEntry;
import Extractocol.BufferExtractor_Request.Basics.ContextTable;
import Extractocol.BufferExtractor_Response.Basics.BFNode_Response;
import Extractocol.BufferExtractor_Response.Basics.ParameterBucket;
import Extractocol.BufferExtractor_Response.UnitHandle.InvokeHandler;

public class SearchMethodHelper
{

	// temporary null
	// 20160603 hnamkoong
	public static List<String> SemanticModel = new ArrayList<String>();

	public static Set<SootMethod> methodFilter(Set<SootMethod> sootMethodSet)
	{
		for (String filter : Constants.searchMethodFilter)
		{
			for (SootMethod sm : sootMethodSet)
			{
				if (sm != null)
				{
					if (sm.getSignature().equals(filter) || sm.getSignature().startsWith("<android.")
							|| sm.getSignature().startsWith("<com.github.kevinsawicki.http.HttpRequest:")
					// ||
					// sm.getSignature().startsWith("<com.squareup.okhttp.OkHttpClient:")
					)
					{
						sootMethodSet.remove(sm);
						break;
					}
				}
			}
		}

		return sootMethodSet;
	}
	
	public static String getDeclaringClass(String ut){
		if(ut == null) 
			return null;
		
		return ut.replaceAll("<", "").replaceAll(">", "").split(":")[0];
	}
	
	public static String getMethod(String ut){
		if(ut == null) 
			return null;
		
		return ut.split(" ")[2].split("\\(")[0];
	}
	
	public static String replaceDelaringClass(String replacement, String originalUnit){
		String targetClass = getDeclaringClass(originalUnit);
		
		return originalUnit.replace(targetClass, replacement);
	}
	
	public static ArrayList<String> getVarClasses(ParameterBucket pb){
		if(pb.ie instanceof InstanceInvokeExpr)
			return pb.varTable.getTypeof(((InstanceInvokeExpr) pb.ie).getBase().toString());
		else
			return null;
	}

	public static Set<SootMethod> SearchMethod(ParameterBucket pb)
	{
		String JimpleUnit = pb.ut.toString().replaceAll("_[0-9]+", "");
		SootMethod parentSm = pb.sm;
		String MethodSig = pb.ie.getMethodRef().getSignature();

		Constants.searchmethodnum = Constants.searchmethodnum + 1;

		Set<SootMethod> SootMethodSet = new HashSet<SootMethod>();

		if (MethodSig.startsWith("<com.squareup.okhttp") || MethodSig.startsWith("<com.fasterxml.jackson")
				|| MethodSig.startsWith("<com.snappydb.DBFactory") || MethodSig.startsWith("<com.esotericsoftware.kryo.util")
				|| MethodSig.startsWith("<java.net.URI") || MethodSig.startsWith("<com.skyhookwireless.wps._sdkob")
				|| MethodSig.startsWith("<java.lang.") || SemanticModel.contains(MethodSig) || MethodSig.startsWith("<org.codehaus.jackson")
				|| MethodSig.startsWith("<android.widget.") || MethodSig.startsWith("<ch.boye.") || MethodSig.startsWith("<okio.ByteString")
				|| MethodSig.startsWith("<com.google.inject.Key") || MethodSig.startsWith("<android.") || MethodSig.startsWith("<org.json.")
				|| MethodSig.startsWith("<com.contextlogic.wish.ui."))
			return methodFilter(SootMethodSet);

		List<String> getCallees = null;
		
		getCallees = Constants.sCFG_Forward.getCalleesAt(parentSm.getSignature(), JimpleUnit.toString());

		// In case of 'Interface Invoke', we can invoke only one method not all of the implementations if we know the base variable type.
		// * Example code: 
		// $r3 = $r0.<com.contextlogic.wish.api.core.WishApiRequest: com.contextlogic.wish.api.core.WishApiCallback callback>;
		// InterfaceInvoke $r3.<com.contextlogic.wish.api.core.WishApiCallback: void onSuccess()>();
		// In this case, we only need to invoke <com.contextlogic.wish.api.service.GetFilteredFeedService$1: onSuccess()> if the type of 'callback' is 'com.contextlogic.wish.api.service.GetFilteredFeedService$1'.
		if((InvokeHandler.isInterfaceInvoke(pb.invokeType) || InvokeHandler.isVirtualInvoke(pb.invokeType)) && getCallees != null){
			// 1. Take the type of the base variable
			ArrayList<String> varClass = getVarClasses(pb);
			
			// 2. If the base has a type
			if(varClass != null){
				if(varClass.size() > 0){
					for(String varType : varClass){
						boolean hasFound = false;
						
						// 3. Find the corresponding method in the list getCallees
						for(String sig: getCallees){
							if(sig.contains(varType)){
								// 4. Remove all the other methods out of the list except the corresponding one
								getCallees.clear();
								getCallees.add(sig);
								
								hasFound = true;
								break;
							}
						}
						if(hasFound)
							break;
					}
				}
			}
		}
		
		// BK Handle overriding. It is because sometimes 'getCallees' does not include proper method.
		if((InvokeHandler.isInterfaceInvoke(pb.invokeType) || InvokeHandler.isVirtualInvoke(pb.invokeType)) && getCallees != null){
			List<String> temp_getCallees = new ArrayList<String>();
			
			for(String callee: getCallees){
				// 1. Find sub-classes
				if(getMethod(callee).equals("<init>"))
					continue;
				
				// 1. Take the type of the base variable
				ArrayList<String> varClasses = getVarClasses(pb);
				
				// 2. Check whether it has sub-class
				if(varClasses != null){
					boolean isReplaced = false;
					
					for(String varClass: varClasses){
						String replaced = replaceDelaringClass(varClass, callee);
						
						// 3. Check whether the sub-class overrides the super's (the current unit's declaring class)
						if(Constants.sCFG_Forward.getMethodOf(replaced) != null){
							// 4. Add the replaced one
							temp_getCallees.add(replaced);
							isReplaced = true;
							break;
						}
					}
					
					// 5. adds the original one if it is not replaced
					if(!isReplaced)
						temp_getCallees.add(callee);
				}
			}
			
			// 6. replace original 'getCallees' with the new one
			getCallees.clear();
			getCallees.addAll(temp_getCallees);
		}

		// Filter by taint function
		if (Constants.searchMethodFilterUsingTaintFunction)
		{
			if (Constants.TaintFunctions.contains(MethodSig))
			{
				SootMethodSet.add(Constants.sCFG_Forward.getMethodOf(MethodSig));
				return SootMethodSet;
			}
		}
		else
		{
			if (getCallees == null)
				return SootMethodSet;

			for (String sig : getCallees)
			{
				SootMethodSet.add(Constants.sCFG_Forward.getMethodOf(sig));
			}
		}

		return SootMethodSet;
	}
}
