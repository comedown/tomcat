package young;

import org.apache.catalina.Globals;
import org.apache.catalina.startup.CatalinaProperties;
import org.apache.catalina.startup.ClassLoaderFactory;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

/**
 *
 */
public class JdkLoggerTest {

    private static final Log LOG = LogFactory.getLog(JdkLoggerTest.class);

    @Test
    public void testJdkLog() {
        LOG.info("哈哈");
    }

    public static void main(String[] args) throws Exception {
//        System.out.println((new File(System.getProperty("user.dir"), ".."))
//                .getCanonicalPath());
//
//        File bootstrapJar = new File(System.getProperty("user.dir"), "bootstrap.jar");
//        System.out.println(bootstrapJar.exists());

//        createClassLoader("common", null);

        testFile();
    }

    public static void testFile() {
        File appBase = new File("webapps");
        String[] children = appBase.list();
        System.out.println(Arrays.toString(children));
    }

    public static ClassLoader createClassLoader(String name, ClassLoader parent) throws Exception {
        // 获取不同类加载器加载的目录
        String value = CatalinaProperties.getProperty(name + ".loader");
        // 资源为空，返回父加载器
        if ((value == null) || (value.equals("")))
            return parent;

        value = replace(value);

        List<ClassLoaderFactory.Repository> repositories = new ArrayList<ClassLoaderFactory.Repository>();

        StringTokenizer tokenizer = new StringTokenizer(value, ",");
        while (tokenizer.hasMoreElements()) {
            String repository = tokenizer.nextToken().trim();
            if (repository.length() == 0) {
                continue;
            }

            // Check for a JAR URL repository
            try {
                @SuppressWarnings("unused")
                URL url = new URL(repository);
                repositories.add(
                        new ClassLoaderFactory.Repository(repository, ClassLoaderFactory.RepositoryType.URL));
                continue;
            } catch (MalformedURLException e) {
                // Ignore
                e.printStackTrace();
            }

            // Local repository
            if (repository.endsWith("*.jar")) {
                repository = repository.substring
                        (0, repository.length() - "*.jar".length());
                repositories.add(
                        new ClassLoaderFactory.Repository(repository, ClassLoaderFactory.RepositoryType.GLOB));
            } else if (repository.endsWith(".jar")) {
                repositories.add(
                        new ClassLoaderFactory.Repository(repository, ClassLoaderFactory.RepositoryType.JAR));
            } else {
                repositories.add(
                        new ClassLoaderFactory.Repository(repository, ClassLoaderFactory.RepositoryType.DIR));
            }
        }

        return ClassLoaderFactory.createClassLoader(repositories, parent);
    }
    /**
     * System property replacement in the given string.
     *
     * <br><br>
     * 解析占位符 ${}
     *
     * @param str The original string
     * @return the modified string
     */
    public static String replace(String str) {
        // Implementation is copied from ClassLoaderLogManager.replace(),
        // but added special processing for catalina.home and catalina.base.
        String result = str;
        int pos_start = str.indexOf("${");
        if (pos_start >= 0) {
            StringBuilder builder = new StringBuilder();
            int pos_end = -1;
            while (pos_start >= 0) {
                builder.append(str, pos_end + 1, pos_start);
                pos_end = str.indexOf('}', pos_start + 2);
                if (pos_end < 0) {
                    pos_end = pos_start - 1;
                    break;
                }
                String propName = str.substring(pos_start + 2, pos_end);
                String replacement;
                if (propName.length() == 0) {
                    replacement = null;
                } else if (Globals.CATALINA_HOME_PROP.equals(propName)) {
                    replacement = System.getProperty("user.dir");
                } else if (Globals.CATALINA_BASE_PROP.equals(propName)) {
                    replacement = System.getProperty("user.dir");
                } else {
                    replacement = System.getProperty(propName);
                }
                if (replacement != null) {
                    builder.append(replacement);
                } else {
                    builder.append(str, pos_start, pos_end + 1);
                }
                pos_start = str.indexOf("${", pos_end + 1);
            }
            builder.append(str, pos_end + 1, str.length());
            result = builder.toString();
        }
        return result;
    }

}
