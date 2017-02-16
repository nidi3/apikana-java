call mvn clean org.codehaus.mojo:versions-maven-plugin:2.2:set -DnewVersion=%1
call mvn -Pparent-deploy deploy

