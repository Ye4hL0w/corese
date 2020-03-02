package fr.inria.corese.compiler.eval;

import fr.inria.corese.kgram.api.core.DatatypeValue;
import fr.inria.corese.kgram.api.core.Edge;
import fr.inria.corese.kgram.api.core.Expr;
import fr.inria.corese.kgram.api.core.Node;
import fr.inria.corese.kgram.core.Eval;
import fr.inria.corese.kgram.core.Exp;
import fr.inria.corese.kgram.core.Mapping;
import fr.inria.corese.kgram.core.Mappings;
import fr.inria.corese.kgram.core.Query;
import fr.inria.corese.kgram.path.Path;
import fr.inria.corese.sparql.api.IDatatype;
import fr.inria.corese.sparql.datatype.DatatypeMap;
import fr.inria.corese.sparql.triple.parser.ASTQuery;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Callback manager for LDScript functions with specific annotations Eval SPARQL
 * processor calls before() and after()
 *
 * @before      function us:before(?q) {}
 * @after       function us:after(?m) {}
 * @produce     function us:produce(?q) {}
 * @candidate   function us:candidate(?q, ?e) {}
 * @result      function us:result(?m) {}
 * @solution    function us:solution(?m) {}

 * @author Olivier Corby, Wimmics INRIA I3S, 2018
 *
 */
public class QuerySolverVisitor extends QuerySolverVisitorBasic {

    private static Logger logger = LoggerFactory.getLogger(QuerySolverVisitor.class);
    
 

    public QuerySolverVisitor(Eval e) {
        super(e);
        overload = new QuerySolverOverload(this);
    }

    
    @Override
    public void init(Query q) {
        // Visitor may be reused by let (?g = construct where)
        if (query == null) {
            query = q;
            ast = (ASTQuery) q.getAST();
            setSelect();
            initialize();
            execInit(q);
        }
    }
    
    void execInit(Query q) {
        // authorize possible update whereas we start select where
        boolean b = eval.getSPARQLEngine().isSynchronized();
        eval.getSPARQLEngine().setSynchronized(true);
        callback(eval, INIT, toArray(q));
        eval.getSPARQLEngine().setSynchronized(b);
        eval.getProducer().start(q);
    }

        
    @Override
    public IDatatype before(Query q) {
        if (query == q) {
            return callback(eval, BEFORE, toArray(q));
        }
        // subquery
        return start(q);
    }
    
    @Override
    public IDatatype after(Mappings map) {
        if (map.getQuery() == query) {
            return callback(eval, AFTER, toArray(map));
        }
        // subquery
        return finish(map);
    }
    
    @Override
    public IDatatype beforeUpdate(Query q) {
        return callback(eval, BEFORE_UPDATE, toArray(q));
    }

    @Override
    public IDatatype afterUpdate(Mappings map) {
        return callback(eval, AFTER_UPDATE, toArray(map));
    }
    
    @Override
    public IDatatype beforeLoad(DatatypeValue path) {
        return callback(eval, BEFORE_LOAD, toArray(path));
    }

    @Override
    public IDatatype afterLoad(DatatypeValue path) {
        return callback(eval, AFTER_LOAD, toArray(path));
    }
    
    @Override
    public IDatatype start(Query q) {
        return callback(eval, START, toArray(q));
    }

    @Override
    public IDatatype finish(Mappings map) {
        return callback(eval, FINISH, toArray(map));
    }
    
    @Override
    public IDatatype insert(DatatypeValue path, Edge edge) {
        return callback(eval, INSERT, toArray(path, edge));
    }
    
    @Override
    public IDatatype delete(Edge edge) {
        return callback(eval, DELETE, toArray(edge));
    }
    
    @Override
    public IDatatype update(Query q, List<Edge> delete, List<Edge> insert) { 
        return callback(eval, UPDATE, toArray(q, toDatatype(delete), toDatatype(insert)));
    }
    
    @Override
    public IDatatype orderby(Mappings map) {
        return sort(eval, ORDERBY, toArray(map));
    }
    
    @Override
    public boolean distinct(Eval eval, Query q, Mapping map) {
        IDatatype key = callback(eval, DISTINCT, toArray(q, map));
        if (key == null) {
            return true;
        }       
        return distinct(eval, q, key);
    }
    
    boolean distinct(Eval eval, Query q, IDatatype key) {
        IDatatype res = getDistinct(eval, q).get(key);
        if (res == null) {
            getDistinct(eval, q).set(key, key);
            return true;
        }
        return false;
    }
    
    /**
     * Query and subquery must have different table for distinct
     * As they have different environment, we assign the table to the environment
     */
    IDatatype getDistinct(Eval eval, Query q) {
        IDatatype dt = distinct.get(eval.getEnvironment());
        if (dt == null) {
            dt = DatatypeMap.map();
            distinct.put(eval.getEnvironment(), dt);
        }
        return dt;
    }
    
    @Override
    public boolean limit(Mappings map) {
        IDatatype dt = callback(eval, LIMIT, toArray(map));
        return dt == null || dt.booleanValue();
    }
    
    @Override
    public int timeout(Node serv) {
        IDatatype dt = callback(eval, TIMEOUT, toArray(serv));
        if (dt == null) {
            return 0;
        }
        return dt.intValue();
    }
    
    @Override
    public int slice(Node serv, Mappings map) {
        IDatatype dt = callback(eval, SLICE, toArray(serv, map));
        if (dt == null) {
            return SLICE_DEFAULT;
        }
        return dt.intValue();
    }
    
        
    @Override
    public IDatatype produce(Eval eval, Node g, Edge q) {  
        return produce1(eval, g, q);
    }
    
    public IDatatype produce1(Eval eval, Node g, Edge q) {  
        return callback(eval, PRODUCE, toArray(g, q));
    }
      
   @Override
    public IDatatype candidate(Eval eval, Node  g, Edge q, Edge e) {  
        return callback(eval, CANDIDATE, toArray(g, q, e));
    }
            
    @Override
    public boolean result(Eval eval, Mappings map, Mapping m) {       
        return result(callback(eval, RESULT, toArray(map, m)));       
    }
    
    boolean result(IDatatype dt) {
        if (dt == null) {
            return true;
        }
        return dt.booleanValue();
    }
    
    @Override
    public IDatatype statement(Eval eval, Node g, Exp e) { 
        return callback(eval, STATEMENT, toArray(g, e));
    }
       
    @Override
    public IDatatype path(Eval eval, Node g, Edge q, Path p, Node s, Node o) {       
        return callback(eval, PATH, toArray(g, q, p, s, o));
    }
    
    @Override
    public boolean step(Eval eval, Node g, Edge q, Path p, Node s, Node o) {  
         return result(callback(eval, STEP, toArray(g, q, p, s, o)));         
    }
       
    @Override
    public IDatatype values(Eval eval, Node g, Exp e, Mappings m) { 
        return callback(eval, VALUES, toArray(g, e, m));    
    }  
    
    @Override
    public IDatatype bind(Eval eval, Node g, Exp e, DatatypeValue dt) { 
        return callback(eval, BIND, toArray(g, e, dt));    
    } 
    
    @Override
    public IDatatype bgp(Eval eval, Node g, Exp e, Mappings m) {       
        return callback(eval, BGP, toArray(g, e, m));
    }
    
    @Override
    public IDatatype join(Eval eval, Node g, Exp e, Mappings m1, Mappings m2) {       
        return callback(eval, JOIN, toArray(g, e, m1, m2));
    }
    
    @Override
    public IDatatype optional(Eval eval, Node g, Exp e, Mappings m1, Mappings m2) {       
        return callback(eval, OPTIONAL, toArray(g, e, m1, m2));
    }
    
    @Override
    public IDatatype minus(Eval eval, Node g, Exp e, Mappings m1, Mappings m2) {       
        return callback(eval, MINUS, toArray(g, e, m1, m2));
    }
    
    @Override
    public IDatatype union(Eval eval, Node g, Exp e, Mappings m1, Mappings m2) {       
        return callback(eval, UNION, toArray(g, e, m1, m2));
    }
    
     @Override
    public IDatatype graph(Eval eval, Node g, Exp e, Mappings m) {       
        return callback(eval, GRAPH, toArray(g, e, m));
    }
    
    
    @Override
    public IDatatype service(Eval eval, Node s, Exp e, Mappings m) {       
        return callback(eval, SERVICE, toArray(s, e, m));
    }
    
    @Override
    public IDatatype query(Eval eval, Node g, Exp e, Mappings m) {       
        return callback(eval, QUERY, toArray(g, e, m));
    }
    
    @Override
    public boolean filter() {      
        Expr exp = eval.getEvaluator().getDefineMetadata(getEnvironment(), FILTER, 3);
        return (exp != null);
    } 
    
    @Override
    public boolean filter(Eval eval, Node g, Expr e, boolean b) {       
        IDatatype dt = callback(eval, FILTER, toArray(g, e, DatatypeMap.newInstance(b)));
        if (dt == null) {
            return b;
        }
        return dt.booleanValue();
    }
    
    @Override
    public IDatatype function(Eval eval, Expr funcall, Expr fundef) {  
        if (isFunction()) {
            IDatatype dt = callback(eval, FUNCTION, toArray(funcall, fundef));       
            return dt;
        }
        return null;
    }
    
    
    @Override
    public boolean having(Eval eval, Expr e, boolean b) {       
        IDatatype dt = callback(eval, HAVING, toArray(e, DatatypeMap.newInstance(b)));
        if (dt == null) {
            return b;
        }
        return dt.booleanValue();
    }
    
    @Override
    public DatatypeValue select(Eval eval, Expr e, DatatypeValue dt) {       
        IDatatype val = callback(eval, SELECT, toArray(e, dt));
        return dt;
    }
    
    @Override
    public DatatypeValue aggregate(Eval eval, Expr e, DatatypeValue dt) {       
        IDatatype val = callback(eval, AGGREGATE, toArray(e, dt));
        return dt;
    }
   

    
    @Override
    public IDatatype error(Eval eval, Expr exp, DatatypeValue[] args) {
        return  overload.error(eval, exp, (IDatatype[]) args);
    }
    
    @Override
    public boolean overload(Expr exp, DatatypeValue res, DatatypeValue dt1, DatatypeValue dt2) {
        // prevent overload within overload
        return ! isRunning() && overload.overload(exp, (IDatatype)res, (IDatatype)dt1, (IDatatype)dt2);
    }
       
   @Override
    public IDatatype overload(Eval eval, Expr exp, DatatypeValue res, DatatypeValue[] args) {
        return overload.overload(eval, exp, (IDatatype) res, (IDatatype[]) args);
    }   
    
    @Override
    public int compare(Eval eval, int res, DatatypeValue dt1, DatatypeValue dt2) {
        if (! isRunning() && overload.overload((IDatatype)dt1, (IDatatype)dt2)) {
            return overload.compare(eval, res, (IDatatype)dt1, (IDatatype)dt2);
        }
        return res;
    }
    
    @Override
    public boolean produce() {
        return accept(PRODUCE) && define(PRODUCE, 2);
    }
    
    @Override
    public boolean candidate() {
        return accept(CANDIDATE) && define(CANDIDATE, 3);
    }
    
    @Override
    public boolean statement() {
        return accept(STATEMENT) && define(STATEMENT, 2);
    }
    

}
