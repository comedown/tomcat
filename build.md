1、git clone git@github.com:apache/tomcat.git
2、根目录创建pom.xml
3、copy tomcat目录
4、配置启动Bootstrap类，jvm系统属性  

	-Dcatalina.home=C:\dev\mine\tomcat
	-Dcatalina.base=C:\dev\mine\tomcat
	-Djava.io.tmpdir=C:\dev\mine\tomcat\temp
	-Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager
	-Djava.util.logging.config.file=C:\dev\mine\tomcat\conf\logging.properties