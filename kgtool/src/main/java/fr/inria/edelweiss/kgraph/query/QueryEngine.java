package fr.inria.edelweiss.kgraph.query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.log4j.Logger;

import fr.inria.acacia.corese.exceptions.EngineException;
import fr.inria.acacia.corese.triple.parser.ASTQuery;
import fr.inria.edelweiss.kgenv.parser.Pragma;
import fr.inria.edelweiss.kgram.api.core.Edge;
import fr.inria.edelweiss.kgram.api.core.Node;
import fr.inria.edelweiss.kgram.core.Exp;
import fr.inria.edelweiss.kgram.core.Mapping;
import fr.inria.edelweiss.kgram.core.Mappings;
import fr.inria.edelweiss.kgram.core.Query;
import fr.inria.edelweiss.kgraph.api.Engine;
import fr.inria.edelweiss.kgraph.core.Graph;

/**
 * Equivalent of RuleEngine for Query
 * Run a set of query
 * 
 * @author Olivier Corby, Edelweiss, INRIA 2010
 *
 */
public class QueryEngine implements Engine {
	private static Logger logger = Logger.getLogger(QueryEngine.class);	
	
	Graph graph;
	QueryProcess exec;
	ArrayList<Query> list;
	
	boolean isDebug = false,
			isActivate = true,
			isWorkflow = false;
	
	QueryEngine(Graph g){
		graph = g;
		exec = QueryProcess.create(g);
		list = new ArrayList<Query>();
	}
	
	public static QueryEngine create(Graph g){
		return new QueryEngine(g);
	}

	public void setDebug(boolean b){
		isDebug = b;
	}
	
	public void addQuery(String q)  {
		 try {
			defQuery(q);
		} catch (EngineException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public Query defQuery(String q) throws EngineException {
		//System.out.println("** QE: \n" + q);
		Query qq = exec.compile(q);
		if (qq != null) {
			ASTQuery ast = (ASTQuery) qq.getAST();
			list.add(qq);
			return qq;
		}
		return null;
	}
	
	public List<Query> getQueries(){
		return list;
	}
	
	
	public boolean  process(){
		if (! isActivate){
			return false;
		}
		
		boolean b = false;
		
		for (Query q : list){
			// TRICKY:
			// This engine is part of a workflow which is processed by graph.init()
			// hence it is synchronized by graph.init() 
			// We are here because a query is processed, hence a (read) lock has been taken
			// tell the query that it is already synchronized to prevent QueryProcess synUpdate
			// to take a write lock that would cause a deadlock
			q.setSynchronized(isWorkflow);
			if (isDebug){
				q.setDebug(isDebug);
				System.out.println(q.getAST());
			}
			Mappings map = exec.query(q);
			b = map.nbUpdate() > 0 || b;
			if (isDebug){
				logger.debug(map + "\n");
			}
		}
		return b;
	}
	
	
	
	public Mappings process(Query q, Mapping m){
		try {
			Mappings map = exec.query(q, m, null);
			return map;
		} catch (EngineException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return Mappings.create(q);
	}

	/**
	 * pname is property name
	 * queries are construct where
	 * find a query with construct {?x pname ?y}
	 * process the query
	 * use case: ProducerImpl getEdges() computed by construct-where 
	 */
	Mappings process(Node start, String pname, int index){
		for (Query q : getQueries()){
			
			if (q.isConstruct()){
				Exp cons = q.getConstruct();
				for (Exp ee : cons.getExpList()){

					if (ee.isEdge()){
						Edge edge = ee.getEdge();
						if (edge.getLabel().equals(pname)){

							Mapping bind = null;
							if (start != null) {
								bind = Mapping.create(edge.getNode(index), start);
							}

							Mappings map = process(q, bind);
							return map;
						}
					}
				}
			}
		}
		return null;
	}

	
	public void setActivate(boolean b) {
		isActivate = b;
	}

	
	public boolean isActivate() {
		return isActivate;
	}

	/**
	 * This method is called by a workflow where this engine is submitted
	 */
	public void init() {	
		isWorkflow = true;
	}

	
	public void remove() {			
	}

	
	public void onDelete() {			
	}

	
	public void onInsert(Node gNode, Edge edge) {				
	}

	
	public void onClear() {				
	}

	public int type() {
		return Engine.QUERY_ENGINE;
	}

	
	public void sort(){
		Collections.sort(list, new Comparator<Query>(){
			@Override
			public int compare(Query q1, Query q2){
				Integer p1 = getLevel(q1);
				Integer p2 = getLevel(q2);
				return p1.compareTo(p2);
			}
		});
	}
	
	int getLevel(Query q){
		ASTQuery ast = (ASTQuery) q.getAST();
		return ast.getPriority();
	}
	

}
