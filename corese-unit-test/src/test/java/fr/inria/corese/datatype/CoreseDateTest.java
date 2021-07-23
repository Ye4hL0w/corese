package fr.inria.corese.datatype;

import static org.junit.Assert.assertEquals;

import com.ibm.icu.util.Calendar;

import org.eclipse.rdf4j.model.Literal;
import org.junit.Test;

import fr.inria.corese.sparql.api.IDatatype;
import fr.inria.corese.sparql.datatype.CoreseCalendar;
import fr.inria.corese.sparql.datatype.CoreseDate;
import fr.inria.corese.sparql.datatype.CoreseDecimal;
import fr.inria.corese.sparql.datatype.DatatypeMap;
import fr.inria.corese.sparql.rdf4j.CoreseDatatypeToRdf4jValue;
import fr.inria.corese.sparql.rdf4j.Rdf4jValueToCoreseDatatype;

public class CoreseDateTest {

    @Test
    public void testDoubleConversionDateNow() {
        CoreseDate corese_dt = (CoreseDate) DatatypeMap.newDate();
        Literal rdf4j_dt = (Literal) CoreseDatatypeToRdf4jValue.convert(corese_dt);
        CoreseDate corese_dt_2 = (CoreseDate) Rdf4jValueToCoreseDatatype.convert(rdf4j_dt);

        assertEquals(corese_dt, corese_dt_2);
    }

    @Test
    public void testDoubleConversionDate() {
        CoreseDate corese_dt = (CoreseDate) DatatypeMap.newDate("2021-07-21T08:08:08.4");
        assertEquals(400, corese_dt.getCalendar().get(Calendar.MILLISECOND));

        Literal rdf4j_dt = (Literal) CoreseDatatypeToRdf4jValue.convert(corese_dt);
        assertEquals(400, rdf4j_dt.calendarValue().getMillisecond());

        CoreseDate corese_dt_2 = (CoreseDate) Rdf4jValueToCoreseDatatype.convert(rdf4j_dt);
        assertEquals(400, corese_dt_2.getCalendar().get(Calendar.MILLISECOND));

        assertEquals(corese_dt, corese_dt_2);
    }

    @Test
    public void testDuplicateCalendar() {
        CoreseDate corese_dt = (CoreseDate) DatatypeMap.newDate("2021-07-21T08:08:08.4");

        CoreseCalendar cal_1 = corese_dt.getCalendar();
        CoreseCalendar cal_2 = cal_1.duplicate();

        assertEquals(cal_1, cal_2);
    }
    
    @Test
    public void testGetSecond() {
        CoreseDate corese_dt = (CoreseDate) DatatypeMap.newDate("2021-07-20T10:00:00.12");
        assertEquals(new CoreseDecimal(0.12), corese_dt.getSecond());
    }

    @Test
    public void w3c() {
        IDatatype dt = DatatypeMap.newDate("2002-10-10T12:00:00");

        IDatatype dt1 = DatatypeMap.newDate("2002-10-10T12:00:00-05:00");
        IDatatype dt2 = DatatypeMap.newDate("2002-10-10T12:00:01-05:00");
        IDatatype dt3 = DatatypeMap.newDate("2002-10-09T12:00:00-05:00");
        IDatatype dt4 = DatatypeMap.newDate("2002-10-10T12:00:00");

        assertEquals(false, dt.le(dt1).booleanValue());
        assertEquals(false, dt.le(dt2).booleanValue());
        assertEquals(false, dt.le(dt3).booleanValue());
        assertEquals(true, dt.le(dt4).booleanValue());
    }

}