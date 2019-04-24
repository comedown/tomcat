package young;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.junit.Test;

/**
 *
 */
public class JdkLoggerTest {

    private static final Log LOG = LogFactory.getLog(JdkLoggerTest.class);

    @Test
    public void testJdkLog() {
        LOG.info("哈哈");
    }

}
