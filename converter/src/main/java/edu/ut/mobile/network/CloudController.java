package edu.ut.mobile.network;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Logger;

import ee.ut.engine.FuzzyLogicEngine;
import ee.ut.fuzzylogic.codeoffload.functions.FunctionException;

public class CloudController {
    private static final String FUZZY_FILE_NAME = "fuzzyFile";
    final static Logger logger = Logger.getLogger(CloudController.class.getName());
    private NetworkManagerClient NM = null;
    byte[] IPAddress = new byte[4];  // cloud address
    int port;                        // cloud port
    Object result = null;
    Object state = null;
    final Object waitob = new Object();
    Vector results = new Vector();

    public CloudController(){
        port = NetInfo.port;
        IPAddress[0] = NetInfo.IPAddress[0];
        IPAddress[1] = NetInfo.IPAddress[1];
        IPAddress[2] = NetInfo.IPAddress[2];
        IPAddress[3] = NetInfo.IPAddress[3];
    }

    private Map<String, Double> getParametersFromFile() {
        Map<String, Double> params = new HashMap<String, Double>();
        File file = new File(FUZZY_FILE_NAME);
        BufferedReader bf;
        try {
            bf = new BufferedReader(new FileReader(file));
            String line;
            while ((line = bf.readLine()) != null) {
                String[] args = line.split(" ");
                params.put(args[0], Double.parseDouble(args[1]));
            }
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return params;
    }
    
    private boolean checkFuzzy() {
        Map<String, Double> params = getParametersFromFile();        

        double gradeOfTruth = 0;
        try {
            gradeOfTruth = FuzzyLogicEngine.calculateFuzzyGrade(params);
        } catch (FunctionException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        if (gradeOfTruth >= 50)
            return true;
        else
            return false;
    }
    
    public Vector execute(Method toExecute, Object[] paramValues, Object state, Class stateDataType) {
        synchronized (waitob){
            this.result = null;
            this.state = null;
            

            if(NM == null){
                NM = new NetworkManagerClient(IPAddress, port);
                NM.setNmf(this);
            }
            if (!checkFuzzy())
                return null;
            new Thread(new StartNetwork(toExecute, paramValues, state, stateDataType)).start();
            
            try {
                waitob.wait(NetInfo.waitTime);
            } catch (InterruptedException e) {
            }
            
            if(this.state != null){
                results.removeAllElements();
                results.add(this.result);
                results.add(this.state);
                return results;
            }else{
                return null;
            }
        }

    }

    public void setResult(Object result, Object cloudModel){
        synchronized (waitob){
            this.result = result;
            this.state = cloudModel;
            waitob.notify();
        }
    }

    class StartNetwork implements Runnable{
        Method toExecute;
        Class[] paramTypes;
        Object[] paramValues;
        Object state = null;
        Class stateDataType = null;


        public StartNetwork(Method toExecute, Object[] paramValues, Object state, Class stateDataType) {
            this.toExecute = toExecute;
            this.paramTypes = toExecute.getParameterTypes();
            this.paramValues = paramValues;
            this.state = state;
            this.stateDataType = stateDataType;
        }


        @Override
        public void run() {
            boolean isconnected = NM.connect();
            if(isconnected){
                NM.send(toExecute.getName(), paramTypes, paramValues, state, stateDataType);
            }
        }

    }
}
