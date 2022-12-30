package snmp;
 
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
//import java.util.logging.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.snmp4j.PDU;

import snmp.Record;
import snmp.commands.SnmpGet;
import static snmp.commands.SnmpGet.NULL_DATA_RECIEVED;

    
class Record{
    
    int index;
    long inoctets;
    long outoctets;
    long speed;
    Date date;
    String ip;
    double in_bw;
    double out_bw;
    String port;
    double data_in_mb;
    double data_out_mb;
    double data_datarate_in_mbps;
    double data_datarate_out_mbps;
    
    /* we have not declared them with their value as they can be either 
        normal octets or HC Octets based on what is supported
    */
    static String oid_inoctetes;
    static String oid_outoctetes;
    public String community = "public";
    static String oid_IFindexes="1.3.6.1.2.1.2.2.1.1";
    static String oid_speed = "1.3.6.1.2.1.2.2.1.5.";
    static String oid_status= "1.3.6.1.2.1.2.2.1.8.";
    static String oid_interface_dscr="1.3.6.1.2.1.2.2.1.2.";
    static String oid_basic_inoctetes = "1.3.6.1.2.1.2.2.1.10."; //so octet nhan tren interface
    static String oid_basic_outoctetes = "1.3.6.1.2.1.2.2.1.16."; // so octet gui qua interface
    static String oid_HCInoctetes = "1.3.6.1.2.1.31.1.1.1.6."; // phien ban 64 bit cua ifinoctet
    static String oid_HCOutoctetes = "1.3.6.1.2.1.31.1.1.1.10."; // phien ban 64 bit cua ifoutoctet
    
    //This is the constructor used when creating a record
    Record(String ip,String port,String community) throws IOException{
        this.port = port;
        this.ip=ip;

        date = new Date();

        String InOctets_oidval = oid_inoctetes+port;
        PDU In_resp_from_get = SnmpGet.snmpGet(ip, community, InOctets_oidval);
        inoctets = SnmpGet.getPDUvalue(In_resp_from_get);

        String OutOctets_oidval = oid_outoctetes+port;
        PDU Out_resp_from_get = SnmpGet.snmpGet(ip, community, OutOctets_oidval);
        outoctets = SnmpGet.getPDUvalue(Out_resp_from_get);

        String IfSpeed_oidval = oid_speed+port;
        PDU speed_resp_from_get = SnmpGet.snmpGet(ip, community, IfSpeed_oidval);
        speed = SnmpGet.getPDUvalue(speed_resp_from_get);

    }

    //This is the copy constructor used in copying the arraylist of records for the graph. 
    Record(Record r){
        this.ip=r.ip;
        this.data_datarate_in_mbps=r.data_datarate_in_mbps;
        this.data_datarate_out_mbps=r.data_datarate_out_mbps;
        this.data_in_mb=r.data_in_mb;
        this.data_out_mb=r.data_out_mb;
        this.date=r.date;
        this.in_bw=r.in_bw;
        this.index=r.index;
        this.inoctets=r.inoctets;
        this.out_bw=r.out_bw;
        this.outoctets=r.outoctets;
        this.port=r.port;
        this.speed=r.speed;
    }
}

public class Datacollector {
    
    static void check_if_HC_octets_present(String ip,String port,String community) throws IOException{
        
        System.out.println("Checking the type of InOctets");
        
        System.out.println("First using HCOctets");
        String oid_HCInoctetes = " 1.3.6.1.2.1.31.1.1.1.6.";
        String oid_HCOutoctetes = " 1.3.6.1.2.1.31.1.1.1.10.";
        
        String InOctets_oidval = oid_HCInoctetes+port;
        PDU In_resp_from_get = SnmpGet.snmpGet(ip, community, InOctets_oidval);
        long inoctets = SnmpGet.getPDUvalue(In_resp_from_get);

        String OutOctets_oidval = oid_HCOutoctetes+port;
        PDU Out_resp_from_get = SnmpGet.snmpGet(ip, community, OutOctets_oidval);
        long outoctets = SnmpGet.getPDUvalue(Out_resp_from_get);
        System.out.println("Got value outoctets:"+outoctets+" inoctets:"+inoctets);
        
        String oid_inoctetes = "1.3.6.1.2.1.2.2.1.10.";
        String oid_outoctetes = "1.3.6.1.2.1.2.2.1.16.";
        
        Record.oid_inoctetes=oid_HCInoctetes;
        Record.oid_outoctetes=oid_HCOutoctetes;
        
        System.out.println("setting oid to inoctets= "+Record.oid_inoctetes+" and outoctets to"+Record.oid_outoctetes);
        
        if( inoctets==NULL_DATA_RECIEVED && outoctets==NULL_DATA_RECIEVED ){
        
            System.out.println("Changing octets oid");
            
            InOctets_oidval = oid_inoctetes+port;
            In_resp_from_get = SnmpGet.snmpGet(ip, community, InOctets_oidval);
            inoctets = SnmpGet.getPDUvalue(In_resp_from_get);
            
            OutOctets_oidval = oid_outoctetes+port;
            Out_resp_from_get = SnmpGet.snmpGet(ip, community, OutOctets_oidval);
            outoctets = SnmpGet.getPDUvalue(Out_resp_from_get);
            
            Record.oid_inoctetes=oid_inoctetes;
            Record.oid_outoctetes=oid_outoctetes;
            
            System.out.println("changed oid to inoctets= "+Record.oid_inoctetes+" and outoctets to"+Record.oid_outoctetes);
        }
    }
    
    //used for circular array
    static int startindex;
    static int endindex;
    
    //size of the circular array
    static int no_of_slots=200;
    
    //size of circular array queue
    static int size_record_queue=10;
    
    static Record tmp_recordings[][]; 
    
    static ArrayList<Integer> list_of_ports=new ArrayList<Integer>();
    static int tmp_index;
    
    //after how many seconds shoulf we update
    static final int update_freq = 10;
    //after how many updates should we calculate bandwidth
    static final int calc_freq=1;
    //after how many calculations of bandwidth should the data be stored in database
    static final int data_store_freq = 1;
    
    //for storing the data stored
    static ArrayList<Record> data_collected=new ArrayList<Record>();
    
    //used for GUI
    static ArrayList<Record> data_collected_copy_for_gui=new ArrayList<Record>();
    
    //used for labels in gui
    static double current_inbw;
    static double current_outbw;
    
    //used for mapping index to port and ip
    static String mapping_table[][];
    
    //slots are used as we car add and remove various devices so 
    //we need a mechanism to add so that we can allocate slot for device to be added
    static ArrayList<Integer> free_slots_list=new ArrayList<Integer>();
    static ArrayList<Integer> used_slots_list=new ArrayList<Integer>();
    
    static ArrayList<Integer> ports_list=new ArrayList<Integer>();
    static ArrayList<String> ip_list=new ArrayList<String>();
    static int index=0;
    
    public static void add(String ip,int port,String community){
        if(!ip_list.contains(ip)||!ports_list.contains(port)){
                if(free_slots_list.size()!=0){
                    index=free_slots_list.get(0);
                }
                else{
                    System.out.println("error No free slots;");
                }
                free_slots_list.remove((Integer)index);
                used_slots_list.add((Integer)index);
                ports_list.add(port);
                ip_list.add(ip);
                mapping_table[index][0]=ip;
                mapping_table[index][1]=""+port;
                mapping_table[index][2]=community;
        }
    }
    
    public static void remove(String ip,int port){
            if(ip_list.contains(ip)&&ports_list.contains(port)){
                for(int i:used_slots_list){
                    if(mapping_table[i][0].equals(ip)&&mapping_table[i][1].equals(""+port)){
                        mapping_table[i]=null;
                        used_slots_list.remove((Integer)i);
                        free_slots_list.add((Integer)i);
                        ports_list.remove((Integer)port);
                        ip_list.remove(ip);
                        break;
                    }
                }
            }
    }
    
    //send to gui
    public static ArrayList<Record> get_current_recordings(){
        System.out.println("sending arraylist of size"+data_collected_copy_for_gui.size());
        return data_collected_copy_for_gui;
    }
    
    //copies to create 
    public static void copyArrayList(){
        //both the copies are referring to same object as only the object references 
        //get copied and not the object themselves
        try{
            for(Record r:data_collected){
                Record new_r = new Record(r);
                data_collected_copy_for_gui.add(new_r);
            }
        }
        catch (Exception ex) {
            gui_javafx.pst.println("Error in copying arraylist for gui Datacollector.copyArrayList()");
            ex.printStackTrace(gui_javafx.pst);
        }
        //data_collected_copy_for_gui.addAll(data_collected);
        
    }
    
    public static void clear_copyArrayList(Date last_shown){
    
        Iterator<Record> iter = data_collected_copy_for_gui.iterator();

        while (iter.hasNext()) {
            Record r = iter.next();

            if(r.date.before(last_shown)){
                iter.remove();
            }
                
        }

    }
    
    //not used now 
    public static void addports(int port){
        if(!list_of_ports.contains(port)){
            list_of_ports.add(port);
        }
        else{
            System.out.println("The port you are trying to add is already added");
        }
    }
    
    static int recordindex=0;
    
    //not used now    
    public static void add_device(String ip,int port){
    
        if(!list_of_ports.contains(port)){
            list_of_ports.add(port);
        }
        else{
            System.out.println("The port you are trying to add is already added");
        }
        
    }
    
    public static void calc_bandwidth(Record data[][],int start,int end){
        int i,j;
        int loop;
        
        if(start<end){
            loop=end-start;
        }
        else{
            loop=(size_record_queue-start)+end;
        }
        //System.out.println("start="+start+" end="+end+" loopsize="+loop);
        for(Integer p:used_slots_list){
           // System.out.println("for Port = "+p);
                                 
            for(j=0,i=start+1;j<loop;i++,j++){
            
                if(data[p][(i-1)%size_record_queue]==null)
                {
                    continue;
                }
                
              //System.out.println("subtracting "+(i%size_record_queue)+" from "+((i-1)%size_record_queue));
                long delta_inoctets=(data[p][i%size_record_queue].inoctets-data[p][(i-1)%size_record_queue].inoctets);
                long delta_outoctets=(data[p][i%size_record_queue].outoctets-data[p][(i-1)%size_record_queue].outoctets);
                
                long ifspeed = data[p][i%size_record_queue].speed;
                long time_difference=(data[p][i%size_record_queue].date.getTime()-data[p][(i-1)%size_record_queue].date.getTime());
                long delta_seconds = TimeUnit.MILLISECONDS.toSeconds(time_difference);
                
                if(delta_seconds<update_freq){
                    delta_seconds=update_freq;
                }
                
                //System.out.println("delta inoctets= "+delta_inoctets+" delta_outoctets "+delta_outoctets);
                //System.out.println("delta_seconds= "+delta_seconds);
                
                data[p][i%size_record_queue].data_in_mb=((double)delta_inoctets)/(1024*1024);
                data[p][i%size_record_queue].data_out_mb=((double)delta_outoctets)/(1024*1024);
                data[p][i%size_record_queue].data_datarate_in_mbps=(data[p][i%size_record_queue].data_in_mb/(double)delta_seconds)*8;
                data[p][i%size_record_queue].data_datarate_out_mbps=(data[p][i%size_record_queue].data_out_mb/(double)delta_seconds)*8;
                
                //System.out.println("data_in_mb "+data[p][i%size_record_queue].data_in_mb+"data_out_mb "+data[p][i%size_record_queue].data_out_mb);
                
                double in_num=(double)delta_inoctets*8*100;
                double in_denom=(double)delta_seconds*ifspeed;
                double inbw=in_num/in_denom;
                
                double out_num=(double)delta_outoctets*8*100;
                double out_denom=(double)delta_seconds*ifspeed;
                double outbw=out_num/out_denom;
                
                inbw= Math.floor(inbw * 100000) / 100000;
                outbw= Math.floor(outbw * 100000) / 100000;
                
                data[p][i%size_record_queue].in_bw=inbw;
                data[p][i%size_record_queue].out_bw=outbw;
                
                //leave counter wraps
                if(delta_inoctets>=0&&delta_outoctets>=0){
                    data_collected.add(data[p][i%size_record_queue]);
                }
                
                current_inbw=inbw;
                current_outbw=outbw;
                
                //System.out.println("inbw = "+inbw+" outbw = "+outbw);
                //System.out.printf("inbw = %.4f  outbw = %.4f \n",inbw,outbw);
            }
        }
            //current_data.add(data);
            //list_index++;
    }
    
    public static void initialise_mapping(){
        mapping_table=new String[no_of_slots][3];
        used_slots_list.clear();
        free_slots_list.clear();
        for(int j=0;j<no_of_slots;j++){
            free_slots_list.add(j);
        }
    }
    //called in data model for records
    public static String get_snmp_description(String ip,int port,String community) throws IOException{
            String oid = Record.oid_interface_dscr+port;
            PDU In_resp_from_get = SnmpGet.snmpGet(ip,community, oid);
            String hexresponse=SnmpGet.getPDUStringvalue(In_resp_from_get);
            //can also be in hexadecimal 
            Pattern pattern =Pattern.compile("[0-9a-fA-f][0-9a-fA-f]:[0-9a-fA-f][0-9a-fA-f]");
            Matcher matcher = pattern.matcher(hexresponse);

            String description=hexresponse;

            if(matcher.find()){
                description="";    
                String newhexstring = hexresponse.replaceAll(":"," ");
                String str[]=newhexstring.split(" ");
                for(String s:str){
                    int num = hex2decimal(s);
                    description += (char)num;
                }
            }
        return description;
    }
    
    public static int hex2decimal(String s) {
             String digits = "0123456789ABCDEF";
             s = s.toUpperCase();
             int val = 0;
             for (int i = 0; i < s.length(); i++) {
                 char c = s.charAt(i);
                 int d = digits.indexOf(c);
                 val = 16*val + d;
             }
         return val;
    }
    
    public static String get_snmp_status(String ip,int port,String community) throws IOException{
            String status="";
            String oid = Record.oid_status+port;
            PDU In_resp_from_get = SnmpGet.snmpGet(ip,community, oid);
            long status_code=SnmpGet.getPDUvalue(In_resp_from_get);
            if(status_code==1){status="up";}
            if(status_code==2){status="down";}
            if(status_code==3){status="testing";}
            if(status_code==5){status="dormant";}
            if(status_code==6){status="notpresent";}
        return status;
    }
    
    public static Double get_snmp_speed(String ip,int port,String community) throws IOException{
        String oid = Record.oid_speed+port;
        PDU In_resp_from_get = SnmpGet.snmpGet(ip,community, oid);
        long speed_in_bps=SnmpGet.getPDUvalue(In_resp_from_get);
        Double speed = ((double)speed_in_bps)/(1024*1024);
        return speed;
    }
    
    static ScheduledExecutorService scheduler;
    
	public static void main(String[] args) throws IOException {
		//snmp.process.process("ping 127.0.0.1",true);
        System.out.println("new circular array based implementation");
//        Timer timer = new Timer();
        
        //list_index=0;
        
        // regular interval at which the task will be performed 100000000
        int interval = update_freq;
        
        //after how many seconds it will start
        int delay = 0;
        
        initialise_mapping();
        
        add("127.0.0.1",7,"public");
        add("127.0.0.1",36,"public");
        add("127.0.0.1",37,"public");
        add("127.0.0.1",39,"public");
        add("127.0.0.1",41,"public");
        add("127.0.0.1",42,"public");
        add("127.0.0.1",43,"public");
        add("127.0.0.1",44,"public");
        add("127.0.0.1",46,"public");
        add("127.0.0.1",47,"public");
        
        
        
        repeatfunctions tt= new repeatfunctions();
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleWithFixedDelay(tt, delay, interval, TimeUnit.SECONDS);
                
	}

	static int time_sec=0;
    
    //incast it goes beyong range of int
    static int timewrap=2147483647-(2147483647%(update_freq*3));
    
    static boolean initialise=true;
    static boolean pause=false;
    static Boolean initialise_in_octets_type=true;

    public static void initialise_data_collection(){
    	try{
            if(initialise){
                    startindex=0;
                    endindex=-1;
                    initialise_mapping();
                    tmp_recordings = new Record[no_of_slots][size_record_queue];
                    System.out.println("Table created");
                    initialise=false;
                }
        }
        catch (Exception ex) {
                        gui_javafx.pst.println("Error in Initialising Datacollector.repeatfunction()");
                        ex.printStackTrace(gui_javafx.pst);
        }
    }
    
    public static String[][] prepare_data(ArrayList<Record> data){
    	//MySql date time datatype format
    	SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    	//temporary buffer for storing the data which is to be written to database
    	String database_data[][];
        int no_of_records=data.size();
        database_data=new String[no_of_records][9];
        int index=0;
        for(Record r:data){
            database_data[index][0]=r.ip;
            
            database_data[index][1]=r.port;
            
            database_data[index][2]=sdf.format(r.date);
            
            Double inbw = r.in_bw;
            Double rounded_inbw= Math.floor(inbw * 10000000) / 10000000;
            database_data[index][3]=rounded_inbw.toString();
            
            Double outbw = r.out_bw;
            Double rounded_outbw= Math.floor(outbw * 10000000) / 10000000;
            database_data[index][4]=rounded_outbw.toString();
            
            Double datain = r.data_in_mb;
            Double rounded_datain= Math.floor(datain * 10000000) / 10000000;
            database_data[index][5]=rounded_datain.toString();
            
            Double dataout = r.data_out_mb;
            Double rounded_dataout= Math.floor(dataout * 10000000) / 10000000;
            database_data[index][6]=rounded_dataout.toString();
            
            Double dataratein = r.data_datarate_in_mbps;
            Double rounded_dataratein= Math.floor(dataratein * 10000000) / 10000000;
            database_data[index][7]=rounded_dataratein.toString();
            
            Double datarateout = r.data_datarate_out_mbps;
            Double rounded_datarateout= Math.floor(datarateout * 10000000) / 10000000;
            database_data[index][8]=rounded_datarateout.toString();
            
            index++;
        }
        return database_data;
    }
    
    public static class repeatfunctions implements Runnable{

    	long startInoctets =0;
        long prevInoctets =0;
        long prevOutoctets =0;
        public void run() {
        	try {
            	System.out.println("Inside repeat function");
                if(initialise_in_octets_type){
                    if(!used_slots_list.isEmpty())
                    {   
                        Iterator<Integer> iter = used_slots_list.iterator();
                        while (iter.hasNext()) {

                            Integer i = iter.next();
                            check_if_HC_octets_present(mapping_table[i][0],mapping_table[i][1],mapping_table[i][2]);
                            break;
                        }

                        initialise_in_octets_type=false;

                    }
                }
                if(!pause){
                    try{
                        Iterator<Integer> iter = used_slots_list.iterator();
                        while (iter.hasNext()) {
                            Integer i = iter.next();
                            tmp_recordings[i][tmp_index%size_record_queue]=new Record(mapping_table[i][0],mapping_table[i][1],mapping_table[i][2]);
                        }
                    }
                    catch (Exception ex) {
                        gui_javafx.pst.println("Error in creating temporary records array Datacollector.repeatfunction() at time:"+time_sec);
                        ex.printStackTrace(gui_javafx.pst);
                    }

                    try{
                        System.out.println("time:"+time_sec);
                        tmp_index++;
                        endindex++;
                        endindex=endindex%size_record_queue;
                    }
                    catch (Exception ex) {
                        gui_javafx.pst.println("Error in modifying indexes for circular array Datacollector.repeatfunction()");
                        ex.printStackTrace(gui_javafx.pst);
                    }

                    try{
                        if(time_sec%(update_freq*calc_freq)==0&&time_sec!=0){
                                calc_bandwidth(tmp_recordings, startindex, endindex);
                                startindex=endindex;
                                copyArrayList();
                        }
                    }
                    catch (Exception ex) {
                        gui_javafx.pst.println("Error in calc_bandwidth  Datacollector.repeatfunction()");
                        ex.printStackTrace(gui_javafx.pst);
                    }
                }

                //so that we collect data only according to frequency specified    
                if( time_sec%(update_freq*calc_freq*data_store_freq)==0&&time_sec!=0 ){
                    try{
                        String data[][] = prepare_data(data_collected);
                        try{
                            if(data==null){
                                System.out.println("NO DATA ( NULL )TO BE INSERTED IN DATABASE");
                            }
                            else{
                            	// add data into table recorded_data
                                database.insert_data(data);
                            }
                        }
                        catch (Exception ex) {
                            gui_javafx.pst.println("Error in inserting data to database Database.insert_data() in Datacollector.repeatfunction()");
                            ex.printStackTrace(gui_javafx.pst);
                        }
                        //clear the temporary list
                        data_collected.clear();
                    }
                    catch (Exception ex) {
                        gui_javafx.pst.println("Error in sending data to database Datacollector.repeatfunction()");
                        ex.printStackTrace(gui_javafx.pst);
                    }

                }
                //update time
                time_sec+=update_freq;  
                if(time_sec>(timewrap)){
                   time_sec=2147483647%update_freq;
                }
            } catch (Exception ex) {
            	gui_javafx.pst.println("Error in Data collecting and processing Datacollector.repeatfunction()");
                ex.printStackTrace(gui_javafx.pst);
            }
        }
    }
}
