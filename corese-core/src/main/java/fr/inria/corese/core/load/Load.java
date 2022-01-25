package fr.inria.corese.core.load;

import com.github.jsonldjava.core.JsonLdError;
import fr.inria.corese.core.load.rdfa.RDFaLoader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.concurrent.locks.Lock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import fr.com.hp.hpl.jena.rdf.arp.ALiteral;
import fr.com.hp.hpl.jena.rdf.arp.ARP;
import fr.com.hp.hpl.jena.rdf.arp.AResource;
import fr.com.hp.hpl.jena.rdf.arp.RDFListener;
import fr.com.hp.hpl.jena.rdf.arp.StatementHandler;
import fr.inria.corese.sparql.exceptions.QueryLexicalException;
import fr.inria.corese.sparql.exceptions.QuerySyntaxException;
import fr.inria.corese.sparql.triple.parser.Constant;
import fr.inria.corese.sparql.triple.parser.LoadTurtle;
import fr.inria.corese.sparql.triple.parser.NSManager;
import fr.inria.corese.core.workflow.WorkflowParser;
import fr.inria.corese.core.workflow.SemanticWorkflow;
import fr.inria.corese.kgram.api.core.Node;
import fr.inria.corese.core.api.Loader;
import fr.inria.corese.core.Graph;
import fr.inria.corese.core.api.DataManager;
import fr.inria.corese.core.api.Log;
import fr.inria.corese.core.query.QueryEngine;
import fr.inria.corese.core.rule.RuleEngine;
import fr.inria.corese.core.load.jsonld.CoreseJsonTripleCallback;
import fr.inria.corese.core.load.jsonld.JsonldLoader;
import fr.inria.corese.core.load.rdfa.CoreseRDFaTripleSink;
import fr.inria.corese.core.load.sesame.ParserLoaderSesame;
import fr.inria.corese.core.load.sesame.ParserTripleHandlerSesame;
import fr.inria.corese.core.query.QueryProcess;
import fr.inria.corese.kgram.core.Query;
import fr.inria.corese.sparql.api.IDatatype;
import fr.inria.corese.sparql.datatype.DatatypeMap;
import fr.inria.corese.sparql.exceptions.EngineException;
import fr.inria.corese.sparql.exceptions.SafetyException;
import fr.inria.corese.sparql.triple.function.term.TermEval;
import fr.inria.corese.sparql.triple.parser.Access;
import fr.inria.corese.sparql.triple.parser.Access.Feature;
import fr.inria.corese.sparql.triple.parser.AccessRight;
import java.io.ByteArrayInputStream;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.URLConnection;
import java.util.ArrayList;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.semarglproject.rdf.core.ParseException;

/**
 * Translate an RDF/XML document into a Graph use ARP
 *
 * @author Olivier Corby, Edelweiss INRIA 2010
 *
 */
public class Load
        implements StatementHandler, RDFListener, org.xml.sax.ErrorHandler,
        Loader {

    public static Logger logger = LoggerFactory.getLogger(Load.class);
    private static int DEFAULT_FORMAT = RDFXML_FORMAT;
    public static String LOAD_FORMAT = ALL_FORMAT_STR;
//    static final String HTTP = "http://";
//    static final String FTP = "ftp://";
    static final String FILE = "file";
    //static final String[] protocols = {HTTP, FTP, FILE};
    static final String OWL = NSManager.OWL; 
    static final String IMPORTS = OWL + "imports";
    // true:  load files into kg:default graph when no named graph is given
    // false: load files into named graphs (name = URI of file) 
    private static boolean DEFAULT_GRAPH = false;
    private static int LIMIT_DEFAULT = Integer.MAX_VALUE;
    private static final String RESOURCE = NSManager.RESOURCE;
    
    int maxFile = Integer.MAX_VALUE;
    Graph graph;
    private DataManager dataManager;
    Log log;
    RuleEngine engine;
    QueryEngine qengine;
    private QueryProcess queryProcess;
    private SemanticWorkflow workflow;
    HashMap<String, String> loaded;
    LoadPlugin plugin;
    BuildImpl build;
    String source;
    boolean debug = !true,
            hasPlugin = false;
    private boolean renameBlankNode = true;
    private boolean defaultGraph = DEFAULT_GRAPH;
    private boolean event = true;
    private boolean transformer = false;
    int nb = 0;
    private int limit = LIMIT_DEFAULT;
    private AccessRight accessRight;
    ArrayList<String> exclude;
    private Access.Level level = Access.Level.USER_DEFAULT;

    
    /**
     * true means load in default graph when no named graph is given
     */
    public static void setDefaultGraphValue(boolean b) {
        DEFAULT_GRAPH = b;
    }
    
    public static boolean isDefaultGraphValue() {
        return DEFAULT_GRAPH;
    }

    Load(Graph g) {
        this();
        set(g);
    }

    public Load() {
        exclude = new ArrayList<>();
        setAccessRight(new AccessRight());
    }

    public static Load create(Graph g) {
        return new Load(g);
    }

    public static Load create() {
        return new Load();
    }

    public static void setDefaultFormat(int f) {
        DEFAULT_FORMAT = f;
    }

    @Override
    public void init(Object o) {
        set((Graph) o);
    }

    public static void setLimitDefault(int max) {
        LIMIT_DEFAULT = max;
    }
    
    public void setLimit(int max) {
        limit = max;
    }

    void set(Graph g) {
        graph = g;
        log = g.getLog();
        loaded = new HashMap<>();
    }

    public void reset() {
    }

    public void exclude(String ns) {
        getExclude().add(ns);
    }
    
    ArrayList<String> getExclude() {
        return exclude;
    }

    public void setEngine(RuleEngine eng) {
        engine = eng;
    }

    @Override
    public RuleEngine getRuleEngine() {
        return engine;
    }

    public void setEngine(QueryEngine eng) {
        qengine = eng;
    }

    public void setPlugin(LoadPlugin p) {
        if (p != null) {
            plugin = p;
            hasPlugin = true;
        }
    }

    public void setBuild(BuildImpl b) {
        if (b != null) {
            build = b;
        }
    }

    public void setMax(int n) {
        maxFile = n;
    }

    public Build getBuild() {
        return build;
    }

    public QueryEngine getQueryEngine() {
        return qengine;
    }

    public void setDebug(boolean b) {
        debug = b;
    }
    
    String uri(String name) {
        return NSManager.toURI(name);
    }

    boolean isURL(String path) {
        try {
            new URL(path);
        } catch (MalformedURLException e) {
            return false;
        }
        return true;
    }

   int getTypeFormat(String contentType, int format) {
       return LoadFormat.getTypeFormat(contentType, format);
   }

    // UNDEF_FORMAT loaded as RDF/XML
    @Override
    public int getFormat(String path) {
        return getFormat(path, UNDEF_FORMAT);
    }
    
    boolean hasFormat(String path){
        return hasFormat(path, UNDEF_FORMAT);
    }
    
    /**
     * format = undef : accept any correct format
     * format = some format : accept this format
     */
    boolean hasFormat(String path, int format){
        if (format == UNDEF_FORMAT){
            return getFormat(path) != UNDEF_FORMAT;
        }
        else {
            return getFormat(path) == format;
        }
    }

    public int getFormat(String path, int proposedFormat) {
        if (proposedFormat != UNDEF_FORMAT) {
            return proposedFormat;
        }
        return LoadFormat.getFormat(path);
    }
    
    public int getFormatDefault(String path, int defaultFormat) {
        int f = LoadFormat.getFormat(path);
        if (f != UNDEF_FORMAT) {
            return f;
        }
        return defaultFormat;
    }
       
    @Override
    public boolean isRule(String path) {
        return getFormat(path) == RULE_FORMAT;
    }

    /**
     * parse directory content
     */
    public void parseDir(String path) throws LoadException {
        parseDir(path, null, false);
    }
    
    public void parseDir(String path, int format) throws LoadException {
        parseDir(path, null, false, format);
    }
 
           
    /**
     * Parse directory (not subdirectory)
     * name is named graph (if not null) else path is named graph
     * base is now the path (it used to be the name)
     */
    public void parseDir(String path, String name) throws LoadException {
        parseDir(path, name, false);
    }
        
    public void parseDirRec(String path) throws LoadException {
        parseDir(path, null, true);
    } 
        
    public void parseDirRec(String path, String name) throws LoadException {
        parseDir(path, name,  true);
    } 
     
    public void parseDir(String path, String name, boolean rec) throws LoadException {
        parseDir(path, name, rec, UNDEF_FORMAT);
    }
  
    public void parseDir(String path, String name, boolean rec, int format) throws LoadException {
        parseDir(new File(path), path, name, rec, format);
    }
    
     /**
     * name is the named graph where to create triples 
     * if name = null name := path of each file
     * Difference with loadWE:
     * recursion on subdirectory when rec = true
     * no recursion on directory with SW extension (even if rec = true)
     * base is now the path of each file (not the name)
     * format: required format unless UNDEF_FORMAT
     */
    void parseDir(File file, String path, String name,  boolean rec, int format) throws LoadException {
        if (file.isDirectory()) {
            if (! path.endsWith(File.separator)){
                path += File.separator;
            }           
            for (String f : file.list()) {
                String pname = path + f;
                if (hasFormat(f, format)) {                                      
                    parseDoc(pname, name);
                }
                else if (rec) {
                    File dir = new File(pname);
                    if (dir.isDirectory()){
                        parseDir(dir, pname, name,  rec, format);
                    }
                }
            }
        } 
        else {           
            parseDoc(path, name);
        }
    }
    
    
    /**
     * Load files according to filter extensions (use ExtensionFilter)
     */
    public void parse(File file, FileFilter ff, String name, boolean rec) throws LoadException {
        if (file.isDirectory()) {
            for (File f : file.listFiles(ff)) {                
               if (! f.isDirectory()){
                   parseDoc(f.getAbsolutePath(), name);
               }       
            }
            if (rec){
                for (File dir : file.listFiles()){
                    if (dir.isDirectory()){
                        parse(dir, ff, name,  rec);
                    }
                }
            }              
        } 
        else if (ff.accept(file)){
            parseDoc(file.getAbsolutePath(), name);
        }       
    }
    
    boolean match(String path, int format){
        if (format == UNDEF_FORMAT){
            return true;
        }
        return getFormat(path) == format;
    }
    
    void parseDoc(String path, String name) throws LoadException {       
        if (debug) {
                logger.info("** Load: " + nb++ + " " + graph.size() + " " + path);
         }
         parse(path, name, path, UNDEF_FORMAT);
    }

    /**
     * format is a suggested format when path has no extension
     * default format is RDF/XML
     */
    @Override
    public void parse(String path) throws LoadException {
        parse(path, null, null, UNDEF_FORMAT);
    }
    
    
    
    public void parse(String path, int format) throws LoadException {
        parse(path, null, null, format);
    }
    
    public void parseWithFormat(String path, int requiredFormat) throws LoadException {
        int format = LoadFormat.getFormat(path);
        if (format == UNDEF_FORMAT) {
            format = requiredFormat;
        }
        parse(path, null, null, format, requiredFormat);
    }
    
    public void parse(String path, int expectedFormat, int requiredFormat) throws LoadException {
        parse(path, null, null, expectedFormat, requiredFormat);
    }
    
    
    @Override
    public void parse(String path, String name) throws LoadException {
        parse(path, name, null, UNDEF_FORMAT);
    }
    
    public void parse(String path, String name, int format) throws LoadException {
        parse(path, name, null, UNDEF_FORMAT);
    }

    /**
     * name: the named graph (if null, name = path)
     * base: base for relative URI (if null, base = path)
     * getFormat: 
     * if format  = UNDEF use path extension if any
     * if format != UNDEF use format (even if it contradicts the extension)
     * use case: rdf/xml file has .xml extension but we want to load it as RDFXML_FORMAT
     * if format is UNDEF and path is URI with content type: use content type format
     */
    @Override
    public void parse(String path, String name, String base, int format) throws LoadException {
        parse(path, name, base, format, UNDEF_FORMAT);
    }
    
    public void parse(String path, String name, String base, int expectedFormat, int requiredFormat) throws LoadException {
        name = target(name, path);
        base   = (base == null)   ? path : base;
        name = uri(name);
        base   = uri(base);
        localLoad(path, base, name, getFormat(path, expectedFormat), requiredFormat);
    }
    
    /**
     * 
     */
    String target(String name, String path){
        if (name == null){
            if (isDefaultGraph()){
                return defaultGraph();
            }
            else {
                return path;
            }
        }
        return name;
    }
    
    public String defaultGraph(){
        Node node = graph.addDefaultGraphNode();
        return node.getLabel();
    }
    
    public void parse(InputStream stream) throws LoadException {
        parse(stream, UNDEF_FORMAT);
    }

    public void parse(InputStream stream, int format) throws LoadException {
        parse(stream, defaultGraph(), format);
    }

    public void parse(InputStream stream, String name, int format) throws LoadException {
       parse(stream, name, name, name, format);
    }

    // TODO: clean arg order
    public void parse(InputStream stream, String path, String name, String base, int format) throws LoadException {
        log("stream");

        try {
            Reader read = reader(stream);
            synLoad(read, path, base, name, format);
        } catch (UnsupportedEncodingException e) {
            throw  LoadException.create(e, path);
        }
    }

        
    private void localLoad(String path, String base, String name, int format) throws LoadException {
        localLoad(path, base, name, format, UNDEF_FORMAT);
    }
    
    /**
       if base = null : base = uri(path)
       if name = null : name = base
       * requiredFormat : when path is URL, set HTTP header to accept this format
       * expectedFormat : expected format according to path extension or specified by user
       * Use one of these format parameter when calling the function
    */
   private void localLoad(String path, String base, String name, int expectedFormat, int requiredFormat) 
            throws LoadException {

        log(path);

        if (expectedFormat == RULE_FORMAT) {
            loadRule(path, base);
            return;
        } else if (expectedFormat == QUERY_FORMAT) {
            loadQuery(path, base);
            return;
        }

        Reader read = null;
        InputStream stream = null;
        int myFormat = (requiredFormat == UNDEF_FORMAT)?expectedFormat:requiredFormat;
        
        try {
            if (NSManager.isResource(path)){
                stream = getResourceStream(path);
                read = reader(stream);
            }
            else if (isURL(path)) {
                URL url = new URL(path);
                String contentType = null;

                if (url.getProtocol().equals(FILE)) {
                    URLConnection c = url.openConnection();
                    c.setRequestProperty(ACCEPT, getActualFormat(myFormat));
                    stream = c.getInputStream();
                    contentType = c.getContentType();
                } else {
                    Service srv = new Service(path);
                    stream = srv.load(path, getActualFormat(path, myFormat));                  
                    contentType = srv.getFormat();
                }
                read = reader(stream);
                if (contentType!=null) {
                    //logger.info("Content-type: " + contentType);
                    myFormat = getTypeFormat(contentType, myFormat);
                }
                //System.out.println("load: " + contentType + " " + myFormat);

            } else {
                read = new FileReader(path);
            }
        } catch (Exception e) {
            logger.error(e.toString());
            logger.error(e.getMessage() + " " + path);
            throw LoadException.create(e, path);
        }

        if (base != null) {
            // ARP needs an URI for base
            base = uri(base);
        } else {
            base = uri(path);
        }

        if (name == null) {
            name = base;
        }
        
        synLoad(read, path, base, name, myFormat); //expectedFormat);

        close(stream);
    }
   
    public InputStream getStream(String path, String... formats) 
            throws LoadException, MalformedURLException, FileNotFoundException, IOException {
        String format = "*";
        if (formats.length>0) {
            format = formats[0];
        }
        InputStream stream;
        
        if (NSManager.isResource(path)) {
            stream = getResourceStream(path);
        } else if (isURL(path)) {
            URL url = new URL(path);
            String contentType = null;
            
            if (url.getProtocol().equals(FILE)) {
                URLConnection c = url.openConnection();
                stream = c.getInputStream();
                contentType = c.getContentType();
            } else {
                Service srv = new Service(path);
                stream = srv.load(path, format);
                contentType = srv.getFormat();
            }
            if (contentType != null) {
                //logger.info("Content-type: " + contentType);
                int myFormat = getTypeFormat(contentType, Load.UNDEF_FORMAT);
            }
            //System.out.println("load: " + contentType + " " + myFormat);

        } else {
            stream = new FileInputStream(path);
        }
        return stream;
    }
   
    String getActualFormat(String path, int myFormat) {
        return getActualFormat(myFormat);
    }
   
    String getActualFormat(int myFormat) {
        if (myFormat != UNDEF_FORMAT) {
            String testFormat = LoadFormat.getFormat(myFormat);
            if (testFormat != null) {
                return testFormat;
            } 
        } 
        return LOAD_FORMAT;
    }
   
   
    /**
     * http://ns.inria.fr/corese/ means load local resource
     */ 
    InputStream getResourceStream(String path) throws LoadException {
        String pname = NSManager.stripResource(path);
        InputStream stream = Load.class.getResourceAsStream(pname);
        if (stream == null) {
            throw  LoadException.create(new IOException(path), path);
        }
        return stream;
    }

    void log(String name) {
        if (graph != null) {
            graph.log(Log.LOAD, name);
            graph.logLoad(name);
        }
    }

    Reader reader(InputStream stream) throws UnsupportedEncodingException {
        return new InputStreamReader(stream);
    }
    
    void close(InputStream stream) throws LoadException {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ex) {
                throw new LoadException(ex);
            }
        }
    }



    void error(Object mes) {
        logger.error(mes.toString());
    }

    public void loadString(String str, int format) throws LoadException {
        loadString(str, defaultGraph(), format);
    }

    public void loadString(String str, String name, int format) throws LoadException {
        loadString(str, name, name, name, format);
    }

    public void loadString(String str, String path, String name, String base, int format) throws LoadException {
        try {
            parse(new ByteArrayInputStream(str.getBytes("UTF-8")), path, name, base, format);
        } catch (UnsupportedEncodingException ex) {
            throw new LoadException(ex);
        }
    }

    public void loadResource(String path, int format) throws LoadException {
        loadResource(path, defaultGraph(), format);
    }

    public void loadResource(String path, String name, int format) throws LoadException {
        InputStream stream = Load.class.getResourceAsStream(path);
        if (stream == null) {
            throw  LoadException.create(new IOException(path), path);
        }
        parse(stream, name, format);
    }

    void synLoad(Reader stream, String path, String base, String name, int format) throws LoadException {
        if (isReadLocked()) {
            throw new LoadException(new EngineException("Read lock while parsing: " + path));
        }
        try {
            lock();
            parse(stream, path, base, name, format);            
        } finally {
            unlock();
        }
    }
    
    
    void lock() {
        if (getQueryProcess() != null && getQueryProcess().isSynchronized()) {
            // already locked
        }
        else {
            writeLock().lock();
        }
    }
    
    void unlock() {
        if (getQueryProcess() != null && getQueryProcess().isSynchronized()) {
            // already locked
        } else {
            writeLock().unlock();
        }
    }
    
    public void parse(Reader stream, String path, String base, String name, int format)  throws LoadException {
        switch (format) {
                case TURTLE_FORMAT:
                case NT_FORMAT:
                    loadTurtle(stream, path, base, name);
                    break;

                case NQUADS_FORMAT:
                    loadWithSesame(stream, path, base, name, RDFFormat.NQUADS);
                    break;
                    
                case TRIG_FORMAT:
                    loadWithSesame(stream, path, base, name, RDFFormat.TRIG);
                    break;

                case RULE_FORMAT:
                    loadRule(stream, name);
                    break;
                    
                case WORKFLOW_FORMAT:
                    loadWorkflow(stream, path);
                    break;
                    
                case QUERY_FORMAT:
                    loadQuery(stream, name);
                    break;

                case RDFA_FORMAT:
                    loadRDFa(stream, path, base, name);
                    break;

                case JSONLD_FORMAT:
                    loadJsonld(stream, path, base, name);
                    break;

                case RDFXML_FORMAT:
                    loadRDFXML(stream, path, base, name);
                    break;
                    
                case OWL_FORMAT:
                    loadRDFXMLOrTurtle(stream, path, base, name);
                    break; 
                    
                case XML_FORMAT:
                case JSON_FORMAT:
                    // skip it
                    break;

                case UNDEF_FORMAT:
                default:
                    parse(stream, path, base, name, (DEFAULT_FORMAT == UNDEF_FORMAT) ? RDFXML_FORMAT : DEFAULT_FORMAT);
            }
    }

    Lock writeLock() {
        return graph.writeLock();
    }
    
    boolean isReadLocked() {
        return graph.isReadLocked();
    }
    
    void loadWorkflow(Reader read, String path) throws LoadException{
        WorkflowParser wp = new WorkflowParser();
        try {
            wp.parse(read, path);
        } catch (SafetyException ex) {
            throw new LoadException(ex).setPath(path);
        }
        setWorkflow(wp.getWorkflowProcess());
    }
    
    /**
     * .owl is RDF/XML or Turtle
     */
    void loadRDFXMLOrTurtle(Reader stream, String path, String base, String name) throws LoadException {
        try {
            loadRDFXML(stream, path, base, name);
        } catch (LoadException e) {
            if (e.getException() != null
                && e.getException().getMessage().contains("{E301}")) {
                parse(path, base, name, TURTLE_FORMAT);
            }
        }
    }

    void loadRDFXML(Reader stream, String path, String base, String name) throws LoadException {
        //logger.info("Load RDF/XML: " + path);
        if (hasPlugin) {
            name = plugin.statSource(name);
            base = plugin.statBase(base);
        }
        String save = getSource();
        source = name;
        build = BuildImpl.create(graph, this);
        build.setLimit(limit);
        build.exclude(getExclude());
        build.setSource(name);
        build.setPath(path);
        build.setDataManager(getDataManager());
        IDatatype dt = DatatypeMap.newResource(path);
        boolean b = true;
        if (isEvent()) {
            b = getCreateQueryProcess().isSynchronized();
        }
        before(dt, b);
        build.start();
        ARP arp = new ARP();
        try {
            arp.setRDFListener(this);
        } catch (java.lang.NoSuchMethodError e) {
        }
        arp.setStatementHandler(this);
        arp.setErrorHandler(this);
        try {
            arp.load(stream, base);
        } catch (SAXException | IOException e) {
            throw LoadException.create(e, arp.getLocator(), path);
        } finally {
            build.finish();
            after(dt, b);
            source = save;
            try {
                stream.close();
            } catch (IOException ex) {
                throw LoadException.create(ex, arp.getLocator(), path);
            }
        }
    }

    void loadTurtle(Reader stream, String path, String base, String name) throws LoadException {
        //logger.info("Load Turtle: " + path);
        CreateImpl cr = CreateImpl.create(graph, this);
        cr.graph(Constant.create(name));
        cr.setRenameBlankNode(renameBlankNode);
        cr.setLimit(limit);
        cr.exclude(getExclude());
        cr.setDataManager(getDataManager());
        cr.start();
        IDatatype dt = DatatypeMap.newResource(path);
        boolean b = true; 
        if (isEvent()) {
            b = getCreateQueryProcess().isSynchronized();
        }
        before(dt, b);
        cr.setPath(path);
        LoadTurtle ld = LoadTurtle.create(stream, cr, base);
        try {
            ld.load();
        } catch (QueryLexicalException e) {
            throw LoadException.create(e, path);
        } catch (QuerySyntaxException e) {
            throw LoadException.create(e, path);
        } finally {
            after(dt, b);
            cr.finish();
        }
    }
    
    void before(IDatatype dt, boolean b) {
        if (isEvent()) {
            getCreateQueryProcess().beforeLoad(dt, b);
        }
    }
    
    void after(IDatatype dt, boolean b) {
        if (isEvent()) {
            getCreateQueryProcess().afterLoad(dt, b);
        }
    }

    // load RDFa
    void loadRDFa(Reader stream, String path, String base, String name) throws LoadException {
        //logger.info("Load RDFa: " + path);
        CoreseRDFaTripleSink sink = new CoreseRDFaTripleSink(graph, null);
        sink.setHelper(renameBlankNode, limit);

        RDFaLoader loader = RDFaLoader.create(stream, base);

        try {
            loader.load(sink);
        } catch (ParseException ex) {
            throw LoadException.create(ex, path);
        }

    }

    // load JSON-LD
    void loadJsonld(Reader stream, String path, String base, String name) throws LoadException {
        //logger.info("Load JSON LD: " + path);

        CoreseJsonTripleCallback callback = new CoreseJsonTripleCallback(graph, name);
        callback.setHelper(renameBlankNode, limit);

        JsonldLoader loader = JsonldLoader.create(stream, base);
        try {
            loader.load(callback);
        } catch (IOException | JsonLdError ex) {
            throw LoadException.create(ex, path);
        }
    }

    //load turtle with parser sesame(openRDF)
    //can surpot format:.ttl, .nt, .nq and .trig
    //now only used for .trig and .nq
    void loadWithSesame(Reader stream, String path, String base, String name, RDFFormat format) throws LoadException {
        logger.info("Load Sesame Parser: " + path);
        ParserTripleHandlerSesame handler = new ParserTripleHandlerSesame(graph, name);
        handler.setHelper(renameBlankNode, limit);
        ParserLoaderSesame loader = ParserLoaderSesame.create(stream, base);

        try {
            loader.loadWithSesame(handler, format);
        } catch (IOException ex) {
            throw LoadException.create(ex, path);
        } catch (RDFParseException ex) {
            throw LoadException.create(ex, path);
        } catch (RDFHandlerException ex) {
            throw LoadException.create(ex, path);
        }
    }

    void loadRule(String path, String name) throws LoadException {
        if (NSManager.isResource(path)) {
            loadRuleResource(path, name);
        } else {
            loadRulePath(path, name);
        }
    }
    
    void loadRulePath(String path, String name) throws LoadException {
        check(Feature.LINKED_RULE, name, TermEval.LINKED_RULE_MESS);
        if (engine == null) {
            engine = RuleEngine.create(graph);
        }

//        if (path.endsWith(LoadFormat.IRULE)) {
//            // individual rule
//            QueryLoad ql = QueryLoad.create();
//            String rule = ql.readWE(path);
//            if (rule != null) {
//                engine.addRule(rule);
//            }
//        } 
//        else {
            // rule base
            RuleLoad load = RuleLoad.create(engine);
            load.setLevel(getLevel());
            try {
                load.parse(path);
            } catch (EngineException ex) {
                throw  LoadException.create(ex, path);
            }
        //}
    }
    
    /**
     * path = http://ns.inria.fr/corese/rule/owl.rul
     * Load as resource
     */
    void loadRuleResource(String path, String name) throws LoadException {
        InputStream stream = null;
        try {
            stream = getResourceStream(path);
            Reader read = new InputStreamReader(stream);
            loadRule(read, name);
        } catch (LoadException ex) {
            throw ex;
        } finally {
            close(stream);
        }
    }
    
 
    public void loadRule(Reader stream, String name) throws LoadException {
        check(Feature.LINKED_RULE, name, TermEval.LINKED_RULE_MESS);
        loadRuleBasic(stream, name);
    }
    
    public void loadRuleBasic(Reader stream, String name) throws LoadException {
        if (engine == null) {
            engine = RuleEngine.create(graph);
        }
        RuleLoad load = RuleLoad.create(engine);
        load.setLevel(getLevel());
        try {
            load.parse(stream);
        } catch (EngineException ex) {
            if (ex.isSafetyException()) {
                ex.getSafetyException().setPath(name);
            }
            throw  LoadException.create(ex, name);
        }
    }

    void loadQuery(String path, String name) throws LoadException {
        if (isTransformer()) {           
            // use case: when load a transformation in a directory 
            // each file .rq is loaded by loadQuery
            // in this case, load is authorized
            // PRAGMA: it may load function definition
            // to prevent it: deny DEFINE_FUNCTION
        }
        else {
            check(Feature.IMPORT_FUNCTION, name, TermEval.IMPORT_MESS);
        }
        if (qengine == null) {
            qengine = QueryEngine.create(graph);
        }
        qengine.setLevel(getLevel());
        QueryLoad load = QueryLoad.create(qengine);
        load.parse(path);
    }

    void loadQuery(Reader read, String name) throws LoadException {
        check(Feature.IMPORT_FUNCTION, name, TermEval.IMPORT_MESS);
        if (qengine == null) {
            qengine = QueryEngine.create(graph);
        }
        qengine.setLevel(getLevel());
        QueryLoad load = QueryLoad.create(qengine);
        load.parse(read);
    }

    @Override
    public void statement(AResource subj, AResource pred, ALiteral lit) {
        build.statement(subj, pred, lit);
    }

    @Override
    public void statement(AResource subj, AResource pred, AResource obj) {
        build.statement(subj, pred, obj);
        if (pred.getURI().equals(IMPORTS)) {
            imports(obj.getURI());
        }
    }

    void imports(String uri)  {
        if (!loaded.containsKey(uri)) {
            loaded.put(uri, uri);
            if (debug) {
                logger.info("Import: " + uri);
            }

            BuildImpl save = build;
//            build = BuildImpl.create(graph, this);
//            build.setLimit(save.getLimit());
            try {
                basicImport(uri);
            } catch (LoadException ex) {
                logger.error(ex.getMessage());
            }
            build = save;
        }
    }
    
    void parseImport(String uri) throws LoadException {
        if (!loaded.containsKey(uri)) {
            loaded.put(uri, uri);
            if (debug) {
                logger.info("Import: " + uri);
            }
            basicImport(uri);
        }
    }
    
    
    void check(Feature feature, String uri, String mes) throws LoadException {
        if (Access.reject(feature, getLevel(), uri)) {
            throw new LoadException(new SafetyException(mes, uri));
        }
    }
    
    // RDF owl:imports <fun.rq>
    void basicImport(String uri) throws LoadException {
        switch (getFormat(uri)) {
            case Loader.QUERY_FORMAT:
            {   
                check(Feature.IMPORT_FUNCTION, uri, TermEval.IMPORT_MESS);
                try {
                    Query q  = QueryProcess.create().parseQuery(uri, getLevel());
                } catch (EngineException ex) {
                    throw  LoadException.create(ex, uri);
                }
            }
                break;

            default: parse(uri);
        }
    }

    @Override
    public void error(SAXParseException exception) throws SAXException {
        // TODO Auto-generated method stub
    }

    @Override
    public void fatalError(SAXParseException exception) throws SAXException {
        // TODO Auto-generated method stub
    }

    @Override
    public void warning(SAXParseException exception) throws SAXException {
        // TODO Auto-generated method stub
    }

    /**
     * Process cos:graph statement TODO: generate different blanks in different
     * graphs
     */
    @Override
    public void setSource(String s) {
        if (s == null) {
            //cur = src;
            build.setSource(getSource());
            return;
        }

        if (hasPlugin) {
            s = plugin.dynSource(s);
        }
        build.setSource(s);
    }

    @Override
    public String getSource() {
        return source;
    }

    public boolean isRenameBlankNode() {
        return renameBlankNode;
    }

    public void setRenameBlankNode(boolean renameBlankNode) {
        this.renameBlankNode = renameBlankNode;
    }

    /**
     * *******************************************************
     */
    @Deprecated
    @Override
    public void load(InputStream stream, String source) throws LoadException {
        load(stream, source, source);
    }

    /**
     * source is the graph name path is a pseudo path that may have an extension
     * and hence specify the input format
     *
     * @deprecated
     */
    public void load(InputStream stream, String source, String path) throws LoadException {
        if (source == null) {
            source = defaultGraph();
        }
        if (path == null) {
            path = defaultGraph();
        }
        // ici source était aussi la base ... (au lieu de path)
        load(stream, path, source, source, getFormat(path));
    }

    @Override
    @Deprecated
    public void load(String path) {
        load(path, null);
    }

    @Override
    @Deprecated
    public void loadWE(String path) throws LoadException {
        loadWE(path, null);
    }

    @Deprecated
    public void loadWE(String path, int format) throws LoadException {
        loadWE(path, null, format);
    }

    @Override
    @Deprecated
    public void load(String path, String src) {
        File file = new File(path);
        if (file.isDirectory()) {
            path += File.separator;
            int i = 0;
            for (String f : file.list()) {
                if (! hasFormat(f)){ 
                    continue;
                }
                if (i++ >= maxFile) {
                    return;
                }
                String name = path + f;
                load(name, src);
            }
        } else {
            if (debug) {
                logger.info("** Load: " + nb++ + " " + graph.size() + " " + path);
            }
            try {
                load(path, src, null);
            } catch (LoadException e) {
                logger.error(e.getMessage());
            }
        }
    }

    @Override
    @Deprecated
    public void loadWE(String path, String src) throws LoadException {
        loadWE(path, src, UNDEF_FORMAT);
    }

    @Deprecated        
     public void loadWE(String path, String source, int format) throws LoadException {
        File file = new File(path);
        if (file.isDirectory()) {
            path += File.separator;
            int i = 0;
            for (String f : file.list()) {
                if (! hasFormat(f)){ //(!suffix(f)) {
                    continue;
                }
                if (i++ >= maxFile) {
                    return;
                }
                String name = path + f;
                loadWE(name, source, format);
            }
        } else {
            if (debug) {
                logger.info("** Load: " + nb++ + " " + graph.size() + " " + path);
            }
            load(path, source, null, format);
        }
    }
    

    @Deprecated
    public void load(String path, int format) throws LoadException {
        localLoad(path, path, path, getFormat(path, format));
    }

    @Deprecated
    public void load(String path, String base, String source) throws LoadException {
        localLoad(path, base, source, getFormat(path));
    }

    @Deprecated
    @Override
    public void load(String path, String base, String source, int format) throws LoadException {
        localLoad(path, base, source, getFormat(path, format));
    }

    @Deprecated
    public void load(InputStream stream) throws LoadException {
        load(stream, UNDEF_FORMAT);
    }

    @Deprecated
    public void load(InputStream stream, int format) throws LoadException {
        load(stream, defaultGraph(), format);
    }

    @Deprecated
    public void load(InputStream stream, String source, int format) throws LoadException {
        load(stream, source, source, source, format);
    }

    @Deprecated
    public void load(InputStream stream, String path, String source, String base, int format) throws LoadException {
        parse(stream, path, source, base, format);
    }

    /**
     * @return the workflow
     */
    public SemanticWorkflow getWorkflow() {
        return workflow;
    }

    /**
     * @param workflow the workflow to set
     */
    public void setWorkflow(SemanticWorkflow workflow) {
        this.workflow = workflow;
    }

    /**
     * @return the defaultGraph
     */
    public boolean isDefaultGraph() {
        return defaultGraph;
    }

    /**
     * @param defaultGraph the defaultGraph to set
     */
    public void setDefaultGraph(boolean defaultGraph) {
        this.defaultGraph = defaultGraph;
    }
    
    QueryProcess getCreateQueryProcess() {
        if (getQueryProcess() == null) {
            setQueryProcess(QueryProcess.create(graph));
        }      
        return getQueryProcess();
    }
    

    /**
     * @return the queryProcess
     */
    public QueryProcess getQueryProcess() {
        return queryProcess;
    }

    /**
     * @param queryProcess the queryProcess to set
     */
    public void setQueryProcess(QueryProcess queryProcess) {
        this.queryProcess = queryProcess;
    }

    /**
     * @return the event
     */
    public boolean isEvent() {
        return event;
    }

    /**
     * @param event the event to set
     */
    public void setEvent(boolean event) {
        this.event = event;
    }

    /**
     * @return the accessRight
     */
    public AccessRight getAccessRight() {
        return accessRight;
    }

    /**
     * @param accessRight the accessRight to set
     */
    public void setAccessRight(AccessRight accessRight) {
        this.accessRight = accessRight;
    }

    /**
     * @return the level
     */
    public Access.Level getLevel() {
        return level;
    }

    /**
     * @param level the level to set
     */
    public void setLevel(Access.Level level) {
        this.level = level;
    }

    /**
     * @return the transformer
     */
    public boolean isTransformer() {
        return transformer;
    }

    /**
     * @param transformer the transformer to set
     */
    public void setTransformer(boolean transformer) {
        this.transformer = transformer;
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public void setDataManager(DataManager dataManager) {
        this.dataManager = dataManager;
    }

}
