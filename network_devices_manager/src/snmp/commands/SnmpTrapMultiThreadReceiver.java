package snmp.commands;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import org.snmp4j.CommandResponder;
import org.snmp4j.CommandResponderEvent;
import org.snmp4j.MessageDispatcherImpl;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.mp.MPv1;
import org.snmp4j.mp.MPv2c;
import org.snmp4j.mp.MPv3;
import org.snmp4j.security.SecurityModels;
import org.snmp4j.security.SecurityProtocols;
import org.snmp4j.security.USM;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.TcpAddress;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultTcpTransportMapping;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.util.MultiThreadedMessageDispatcher;
import org.snmp4j.util.ThreadPool;

public class SnmpTrapMultiThreadReceiver implements CommandResponder {
	private MultiThreadedMessageDispatcher dispatcher;
	private Snmp snmp = null;
	private Address listenAddress;
	private ThreadPool threadPool;

	private void init() throws UnknownHostException, IOException {
		threadPool = ThreadPool.create("TrapPool", 2);
		dispatcher = new MultiThreadedMessageDispatcher(threadPool, new MessageDispatcherImpl());
		listenAddress = GenericAddress.parse(System.getProperty( "snmp4j.listenAddress", "udp:127.0.0.1/162"));
		TransportMapping transport;
		if (listenAddress instanceof UdpAddress) {
			transport = new DefaultUdpTransportMapping(
					(UdpAddress) listenAddress);
		} else {
			transport = new DefaultTcpTransportMapping(
					(TcpAddress) listenAddress);
		}
		snmp = new Snmp(dispatcher, transport);
		snmp.getMessageDispatcher().addMessageProcessingModel(new MPv1());
		snmp.getMessageDispatcher().addMessageProcessingModel(new MPv2c());
		snmp.getMessageDispatcher().addMessageProcessingModel(new MPv3());
		USM usm = new USM(SecurityProtocols.getInstance(), new OctetString(MPv3.createLocalEngineID()), 0);
		SecurityModels.getInstance().addSecurityModel(usm);
		snmp.listen();
	}

	public void run() {
		System.out.println("----> Trap Receiver run ... <----");
		try {
			init();
			snmp.addCommandResponder(this);
			System.out.println("----> Bắt đầu nghe trên cổng, đợi Trap message  <----");
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

//	@SuppressWarnings("unchecked")
	public static ArrayList<String[]> list = new ArrayList<String[]>();
	public void processPdu(CommandResponderEvent event) {
		System.out.println("----> Bắt đầu phân tích ResponderEvent: <----");
		if (event == null || event.getPDU() == null) {
			System.out.println("[Warn] ResponderEvent or PDU is null");
			return;
		}
		List<? extends VariableBinding> vbVect = event.getPDU().getVariableBindings();
		for (VariableBinding vb : vbVect) {
			System.out.println(vb.getOid() + " = " + vb.getVariable());
			list.add(new String[] { vb.getOid().toString(), vb.getVariable().toString() });
		}
		System.out.println("---->  Quá trình phân tích ResponderEvent đã kết thúc <----");
	}

	public static void main(String[] args) {
		SnmpTrapMultiThreadReceiver trapReceiver = new SnmpTrapMultiThreadReceiver();
		trapReceiver.run();
	}
}
