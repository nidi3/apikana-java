package org.swisspush.apikana;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Package the generated files in sources-jar, api-jar and style-jar.
 */
@Mojo(name = "package", defaultPhase = LifecyclePhase.PACKAGE)
public class PackageMojo extends AbstractGenerateMojo {

    public void execute() throws MojoExecutionException {
        try {
            if (isPom()) {
                getLog().info("Packaging is pom. Skipping execution.");
                mavenProject.getProperties().setProperty("jsonschema2pojo.skip", "true");
                if (file(style).exists()) {
                    projectHelper.attachArtifact(mavenProject, "jar", "style", createStyleJar());
                }
            } else {
                projectHelper.attachArtifact(mavenProject, createApiJar(OUTPUT), "api");
                projectHelper.attachArtifact(mavenProject, createSourcesJar(OUTPUT), "sources");
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Problem packaging APIs", e);
        }
    }

    private File createApiJar(String output) throws IOException {
        final Manifest manifest = new Manifest();
        final Attributes mainAttributes = manifest.getMainAttributes();
        mainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        mainAttributes.put(Attributes.Name.MAIN_CLASS, ApiServer.class.getName());
        final File file = apiJarFile();
        try (JarOutputStream zs = new JarOutputStream(new FileOutputStream(file), manifest)) {
            IoUtils.addDirToZip(zs, file(output + "/model/json-schema-v3"), "model/json-schema-v3");
            IoUtils.addDirToZip(zs, file(output + "/model/json-schema-v4"), "model/json-schema-v4");
            IoUtils.addDirToZip(zs, file(output + "/ui"), "ui");
            IoUtils.addClassToZip(zs, ApiServer.class);
            IoUtils.addClassToZip(zs, ApiServer.PathResourceHandler.class);
            addJettyToZip(zs);
            IoUtils.addDirToZip(zs, file(output + "/model/openapi"), "model/openapi");
            IoUtils.addDirToZip(zs, file(output + "/model/ts"), "model/ts");
            IoUtils.addDirToZip(zs, target("api-dependencies/ts"), "model/ts/node_modules");
        }
        return file;
    }

    private File createSourcesJar(String output) throws IOException {
        final File file = sourcesJarFile();
        try (JarOutputStream zs = new JarOutputStream(new FileOutputStream(file))) {
            IoUtils.addDirToZip(zs, target("java-gen"), "");
            IoUtils.addDirToZip(zs, file(output + "/model/java"), "");
            IoUtils.addDirToZip(zs, file(output + "/model/json-schema-v3"), "json-schema-v3");
            IoUtils.addDirToZip(zs, file(output + "/model/json-schema-v4"), "json-schema-v4");
            IoUtils.addDirToZip(zs, file(output + "/model/openapi"), "openapi");
            IoUtils.addDirToZip(zs, file(output + "/model/ts"), "ts");
            IoUtils.addDirToZip(zs, file(output + "/ui/style"), "style");
        }
        return file;
    }

    private File createStyleJar() throws IOException {
        final File file = styleJarFile();
        file.getParentFile().mkdirs();
        try (JarOutputStream zs = new JarOutputStream(new FileOutputStream(file))) {
            IoUtils.addDirToZip(zs, file(style), "style");
        }
        return file;
    }

    private void addJettyToZip(JarOutputStream zs) throws IOException {
        IoUtils.addZipsToZip(zs, "org/eclipse/jetty");
        IoUtils.addZipsToZip(zs, "javax/servlet");
    }

    private File apiJarFile() {
        return target(mavenProject.getArtifactId() + "-" + mavenProject.getVersion() + "-api.jar");
    }

    private File sourcesJarFile() {
        return target(mavenProject.getArtifactId() + "-" + mavenProject.getVersion() + "-sources.jar");
    }

    private File styleJarFile() {
        return target(mavenProject.getArtifactId() + "-" + mavenProject.getVersion() + "-style.jar");
    }

}
