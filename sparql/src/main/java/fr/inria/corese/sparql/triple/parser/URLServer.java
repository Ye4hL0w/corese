package fr.inria.corese.sparql.triple.parser;

import fr.inria.corese.kgram.api.core.Node;
import fr.inria.corese.sparql.api.Graph;
import fr.inria.corese.sparql.api.IDatatype;
import fr.inria.corese.sparql.triple.function.term.Binding;
import java.util.ArrayList;
import java.util.HashMap;

import java.util.List;

/**
 *
 * @author corby
 */
public class URLServer implements URLParam {
    
    static HashMap<String, Boolean> encode;
    
    // whole URL: http://corese.inria.fr/sparql?param=value
    private String url;
    // server part of URL: http://corese.inria.fr/sparql
    private String server;
    // param=value&...
    private String param;
    private HashMapList<String> amap;
    // service Node for service clause
    private Node node;
    private Graph graph;
    private int number = -1;
    
    static {
        start();
    }
    
    
    public URLServer(String s) {
        url = s;
        init();
    }
    
    // service clause
    public URLServer(Node node) {
        this(node.getLabel());
        setNode(node);
    }
    
    @Override
    public String toString() {
        return getURL();
    }       
    
    void init() {
        setServer(server(getURL()));
        setParam(parameter(getURL()));
        if (getParam()!=null) {
            setMap(hashmap(getParam()));
            if (hasParameter(DISPLAY, PARAM)) {
                display();
            }
        }
    }
    
    public void display() {
        getMap().keySet().forEach((key) -> {
            System.out.println(String.format("%s=%s", key, getParameterList(key)));
        });
    }
    
    
    String server(String url) {
        int index = url.indexOf("?");
        if (index == -1) {
            return url;
        }
        return url.substring(0, index);
    }
    

    
    String parameter(String url) {
//        try {
//            URLDecoder.decode(url, StandardCharsets.UTF_8.toString());
//        } catch (UnsupportedEncodingException ex) {
        //}
        int index = url.indexOf("?");
        if (index == -1) {
            return null;
        }
        return url.substring(index+1);
    }
    
    public String getParameter(String name) {
        if (getMap() == null) {
            return null;
        }
        return getMap().getFirst(name);
    }
    
    public String getVariable(String name) {
        String value = getParameter(name);        
        if (value != null && isVariable(value)) {
            return extractVariable(value);
        }
        return null;
    }
    
    String extractVariable(String value) {
        return value.substring(1, value.length()-1);
    }
    
    /**
     * var not in skip AND/OR var in focus
     */
    public boolean accept(String var) {
        List<String> focus = getParameterList(FOCUS);
        List<String> skip  = getParameterList(SKIP);
        if (skip != null && skip.contains(var)) {
            return false;
        }
        if (focus != null && ! focus.contains(var)) {
            return false;
        }
        return true;
    } 
    
    public boolean hasParameter() {
        return getMap() != null && !getMap().isEmpty();
    }
    
    public boolean hasParameter(String name) {
        return getParameterList(name) != null;
    }
    
    public boolean hasParameter(String name, String value) {
        List<String> list = getParameterList(name);
        if (list == null) {
            return false;
        }
        return list.contains(value);
    }
    
    public List<String> getParameterList(String name) {
        if (getMap() == null) {
            return null;
        }
        return getMap().get(name);
    }
    
    public int intValue(String name) {
        if (getMap() == null) {
            return -1;
        }
        String value = getMap().getFirst(name);
        if (value == null) {
            return -1;
        }
        try { return Integer.valueOf(value); }
        catch (Exception ex) { return -1;}
    }
    
    public int intValue(String name, int def) {
        int value = intValue(name);
        if (value == -1) {
            return def;
        }
        return value;
    }

    /**
     * param = URL parameter string
     * create hashmap for parameter value
     * hashmap: param -> list(value)
     */
    HashMapList hashmap(String param) {
        HashMapList<String> map = new HashMapList();
        String[] params = param.split("&");
        for (String str : params) {
            //System.out.println("URL param: " + str);
            String[] keyval = str.split("=");
            if (keyval.length>=2)   {
                String key = keyval[0];
                String val = keyval[1];
                List<String> list = map.getCreate(key);               
                if (! list.contains(val)) {
                    list.add(val);
                }
            }
        }
        return map;
    }
    
    /**
     * ?param={?this}
     * get ?this=value in Binding
     * set param=value
     */
    public void complete(Binding b) {
        if (getMap() == null) {
            return;
        }
        
        for (String key : getMap().keySet()) {
            String val = getParameter(key);
            if (isVariable(val)) {
                 IDatatype dt = b.getGlobalVariable(extractVariable(val));
                 if (dt != null) {  
                     getMap().setFirst(key, dt.getLabel());
                 }
            }
        }
    }
    
    
    static void start() {
        encode = new HashMap<>();
        // boolean means share in federate mode
        encode.put(ACCEPT, false);
        encode.put(REJECT, false);
        encode.put(TIMEOUT, true);
    }
    
    /**
     * encode key=val as param=key~val for corese server SPARQL API parameter passing
     */
    public static boolean isEncoded(String name) {
        if (encode.containsKey(name)) {
            return true;
        }
        return false;
    }
    
    static boolean isShare(String name) {
        return encode.containsKey(name) && encode.get(name);
    }
    
    /**
     * Some parameter key=val are encoded as param=key~val
     * Because corese server have limited number of parameters in SPARQL API
     */
    public void encode() {
        if (getMap() == null) {
            return;
        }
        
        List<String> param = getMap().get(PARAM);
        if (param == null) {
            param = new ArrayList<>();
        }
        
        for (String key : getMap().keySet()) {
            if (isEncoded(key)) {
                param.addAll(toParam(key, getMap().get(key)));
            } 
        }
        
        if (! param.isEmpty()){
            getMap().put(PARAM, param);
        }
    }
    
    
    /**
     * Decode parameter  param=key~val as key=val in Context
     */
    public static void decode(Context c, String param) {
        if (param.contains(":") || param.contains(SEPARATOR)) {
            String sep = param.contains(SEPARATOR) ? SEPARATOR : ":";
            String[] list = param.split(sep);

            if (list.length >= 2) {
                String key = list[0];
                if (isEncoded(key)) {
                    if (isShare(key)) {
                        // timeout
                        if (!c.hasValue(key)) {
                            c.add(EXPORT, key);
                        }
                        c.set(key, list[1]);
                    } else {
                        // accept reject
                        c.add(key, list[1]);
                    }
                }
            }
        }
    }
    
    /**
     * str -> accept~str
     */
    List<String> toParam(String name, List<String> list) {
        ArrayList<String> param = new ArrayList<>();
        for (String value : list) {
            param.add(String.format("%s~%s", name, value));
        }
        return param;
    }
    
    boolean isVariable(String value) {
        return value.startsWith("{") && value.endsWith("}");
    }
    
    public boolean hasMethod() {
        return getParameter("method") != null;
    }
    
    public boolean isGET() {
        String method = getParameter("method");
        return method != null && method.equals("get");
    }
    
    /**
     * @return the param
     */
    public String getParam() {
        return param;
    }

    /**
     * @param param the param to set
     */
    public void setParam(String param) {
        this.param = param;
    }
    
    public String getServer() {
        return server;
    }
    
    public void setServer(String s) {
        server = s;
    }

    /**
     * @return the map
     */
    public HashMapList<String> getMap() {
        return amap;
    }

    /**
     * @param map the map to set
     */
    public void setMap(HashMapList<String> map) {
        this.amap = map;
    }

    /**
     * @return the url
     */
    public String getURL() {
        return url;
    }
    
    public String getLogURL() {
        return getURL();
    }
    
    public String getLogURLNumber() {
        return String.format("%s.%s", getServer(), getNumber());
    }
    
    /**
     * @return the node
     */
    public Node getNode() {
        return node;
    }

    /**
     * @param node the node to set
     */
    public void setNode(Node node) {
        this.node = node;
    }

    /**
     * @return the graph
     */
    public Graph getGraph() {
        return graph;
    }

    /**
     * @param graph the graph to set
     */
    public void setGraph(Graph graph) {
        this.graph = graph;
    }

    /**
     * @return the number
     */
    public int getNumber() {
        return number;
    }

    /**
     * @param number the number to set
     */
    public void setNumber(int number) {
        this.number = number;
    }
    
}
