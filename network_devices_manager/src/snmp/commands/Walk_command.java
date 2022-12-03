package snmp.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.Null;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import static snmp.Datacollector.hex2decimal;
import snmp.Gui_fxmlController;

public class Walk_command {
	
	static ArrayList<String> result;
	static int no_of_values=0;
	public static final int DEFAULT_VERSION = SnmpConstants.version2c;
	public static final String DEFAULT_PROTOCOL = "udp";
	public static final int DEFAULT_PORT = 161;
	public static final long DEFAULT_TIMEOUT = 3 * 1000L;
	public static final int DEFAULT_RETRY = 3;
	
	public static CommunityTarget createDefault(String ip, String community) {
		Address address = GenericAddress.parse(DEFAULT_PROTOCOL + ":" + ip
				+ "/" + DEFAULT_PORT);
		CommunityTarget target = new CommunityTarget();
		target.setCommunity(new OctetString(community));
		target.setAddress(address);
		target.setVersion(DEFAULT_VERSION);
		target.setTimeout(DEFAULT_TIMEOUT); // milliseconds
		target.setRetries(DEFAULT_RETRY);
		return target;
	}

	/**
	 * @param ip
	 * @param community
	 * @param targetOid
	 */
	public static ArrayList<String> snmpWalk(String ip, String community, String targetOid) {
        result = new ArrayList<String>();
		CommunityTarget target = SnmpUtil.createDefault(ip, community);
		TransportMapping transport = null;
		Snmp snmp = null;
		try {
			transport = new DefaultUdpTransportMapping();
			snmp = new Snmp(transport);
			transport.listen();

			PDU pdu = new PDU();
			OID targetOID = new OID(targetOid);
			pdu.add(new VariableBinding(targetOID));

			boolean finished = false;
			while (!finished) {
				VariableBinding vb = null;
				ResponseEvent respEvent = snmp.getNext(pdu, target);

				PDU response = respEvent.getResponse();

				if (null == response) {
					Gui_fxmlController.create_dialog("No Response from SNMP AGENT");
                    System.out.println("responsePDU == null");
					finished = true;
					break;
				} else {
					vb = response.get(0);
				}
				// check finish
				finished = checkWalkFinished(targetOID, pdu, vb);
				if (!finished) {
					//System.out.println("==== walk each vlaue :");
					String value = vb.getVariable().toString();
					Pattern pattern =Pattern.compile("[0-9a-fA-f][0-9a-fA-f]:[0-9a-fA-f][0-9a-fA-f]");
                    Matcher matcher = pattern.matcher(value);
                    
                   if(matcher.find()){
                        String hexresponse = value;
                        String newhexstring = hexresponse.replaceAll(":"," ");
                        String str[]=newhexstring.split(" ");
                        String description="";
                        for(String s:str){
                                int num = hex2decimal(s);
                                description += (char)num;
                            }
                        value=description;
                    }
                    result.add(value);
                    
                    no_of_values++;
                    //System.out.println(""+no_of_values);
					//if(value.contains(":")){
					//	System.out.println(vb.getOid() + " ==  " + HexStrConver.testHex2Str(value));
					//}else{
					//System.out.println(vb.getOid() + " = " + vb.getVariable());
					//}

					// Set up the variable binding for the next entry.
					pdu.setRequestID(new Integer32(0));
					pdu.set(0, vb);
				} else {
					//System.out.println("SNMP walk OID has finished.");
					snmp.close();
				}
			}
			//System.out.println("----> demo end <----");
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("SNMP walk Exception: " + e);
		} finally {
			if (snmp != null) {
				try {
					snmp.close();
				} catch (IOException ex1) {
					snmp = null;
				}
			}
		}
                
        return result;

	}

	/**
	 * 1)responsePDU == null<br>
	 * 2)responsePDU.getErrorStatus() != 0<br>
	 * 3)responsePDU.get(0).getOid() == null<br>
	 * 4)responsePDU.get(0).getOid().size() < targetOID.size()<br>
	 * 5)targetOID.leftMostCompare(targetOID.size(),responsePDU.get(0).getOid())
	 * !=0<br>
	 * 6)Null.isExceptionSyntax(responsePDU.get(0).getVariable().getSyntax())<br>
	 * 7)responsePDU.get(0).getOid().compareTo(targetOID) <= 0<br>
	 *
	 * @param targetOID
	 * @param pdu
	 * @param vb
	 * @return
	 */
	private static boolean checkWalkFinished(OID targetOID, PDU pdu,
			VariableBinding vb) {
		boolean finished = false;
		if (pdu.getErrorStatus() != 0) {
			System.out.println("[true] responsePDU.getErrorStatus() != 0 ");
			System.out.println(pdu.getErrorStatusText());
			finished = true;
		} else if (vb.getOid() == null) {
			System.out.println("[true] vb.getOid() == null");
			finished = true;
		} else if (vb.getOid().size() < targetOID.size()) {
			System.out.println("[true] vb.getOid().size() < targetOID.size()");
			finished = true;
		} else if (targetOID.leftMostCompare(targetOID.size(), vb.getOid()) != 0) {
			System.out.println("[true] targetOID.leftMostCompare() != 0");
			finished = true;
		} else if (Null.isExceptionSyntax(vb.getVariable().getSyntax())) {
			System.out
					.println("[true] Null.isExceptionSyntax(vb.getVariable().getSyntax())");
			finished = true;
		} else if (vb.getOid().compareTo(targetOID) <= 0) {
			System.out.println("[true] Variable received is not "
					+ "lexicographic successor of requested " + "one:");
			System.out.println(vb.toString() + " <= " + targetOID);
			finished = true;
		}
		return finished;

	}

	/**
	 * 
	 * @param args
	 */
        public static void main(String[] args) {
        	String ip = "10.2.51.56";
        	String community = "public";
        	// 1.3.6.1.2.1.2.2.1.2
        	String targetOid = ".1.3.6.1.2.1.17.4.3";//".1.3.6.1.2.1.1";
        	Walk_command.snmpWalk(ip, community, targetOid);
        	System.out.println("ended");
        }

}
