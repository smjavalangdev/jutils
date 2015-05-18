package sm.utils;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import org.junit.Test;
import sm.utils.Main;


public class MainTest {
    @Test
    public void testTriple() {
        assertThat(Main.triple("AB"), equalTo("ABABAB"));
    }
}
