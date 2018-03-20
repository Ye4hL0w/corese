package fr.inria.corese.w3c;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
import fr.inria.corese.sparql.exceptions.EngineException;
import fr.inria.corese.core.Graph;
import fr.inria.corese.core.query.QueryProcess;
import fr.inria.corese.core.load.Load;
import fr.inria.corese.core.load.LoadException;
import fr.inria.corese.core.print.JSOND3Format;
import fr.inria.corese.core.print.JSONFormat;
import fr.inria.corese.kgram.core.Mappings;
import java.io.InputStream;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author Alban Gaignard <alban.gaignard@cnrs.fr>
 */
public class JSONTest {

    public JSONTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void helloJsonD3() throws LoadException, EngineException {
        InputStream is = JSONTest.class.getClassLoader().getResourceAsStream("kgram1-persons.rdf");
        Graph g = Graph.create();
        Load ld = Load.create(g);
        ld.load(is);

        String query = "SELECT * WHERE {?x ?p ?y}";
        QueryProcess qp = QueryProcess.create(g);
        Mappings maps = qp.query(query);
        
        String mapsProvJson = "{ \"mappings\" : "
                + JSONFormat.create(maps).toString()
                + " , "
                + "\"provenance\" : "
                + JSOND3Format.create(g).toString()
                + " }";

        assertEquals(3005, mapsProvJson.length());
    }
}
