package org.swisspush.apikana;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.artifact.ProjectArtifact;
import org.apache.maven.repository.RepositorySystem;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.twdata.maven.mojoexecutor.MojoExecutor.*;

public abstract class AbstractGenerateMojo extends AbstractMojo {
    protected final static String OUTPUT = "target/api";

    @Parameter(defaultValue = "${project}", readonly = true)
    protected MavenProject mavenProject;

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession mavenSession;

    @Component
    private BuildPluginManager pluginManager;

    @Component
    protected MavenProjectHelper projectHelper;

    @Component
    private RepositorySystem repositorySystem;

    @Component
    private ProjectBuilder projectBuilder;

    /**
     * The working directory for node.
     */
    @Parameter(defaultValue = "target/node", property = "apikana.node-working-dir")
    private File nodeWorkingDir;

    /**
     * The directory containing css files and images to style the swagger GUI.
     */
    @Parameter(defaultValue = "src/style", property = "apikana.style")
    protected String style;

    protected void unpackModelDependencies() throws IOException {
        for (final Artifact a : mavenProject.getArtifacts()) {
            JarFile jar = classifiedArtifactJar(a, "sources");
            if (jar != null) {
                final Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    final JarEntry entry = entries.nextElement();
                    copyModel(jar, entry, "", "ts", a.getArtifactId());
                    copyModel(jar, entry, "", "json-schema-v3", a.getArtifactId());
                    copyModel(jar, entry, "", "json-schema-v4", a.getArtifactId());
                    copyModel(jar, entry, "", "style", "");
                }
            }
        }
    }

    protected void unpackStyleDependencies(MavenProject project) throws IOException {
        if (project != null) {
            JarFile jar = classifiedArtifactJar(new ProjectArtifact(project), "style");
            if (jar != null) {
                final Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    final JarEntry entry = entries.nextElement();
                    copyModel(jar, entry, "", "style", "");
                }
            }
            unpackStyleDependencies(project.getParent());
        }
    }

    private JarFile classifiedArtifactJar(Artifact a, String classifier) throws IOException {
        Artifact artifact = classifiedArtifact(a, classifier);
        return artifact == null ? null : new JarFile(artifact.getFile());
    }

    private Artifact classifiedArtifact(Artifact a, String classifier) {
        final ArtifactResolutionRequest req = new ArtifactResolutionRequest();
        req.setArtifact(repositorySystem.createArtifactWithClassifier(a.getGroupId(), a.getArtifactId(), a.getVersion(), "jar", classifier));
        final ArtifactResolutionResult result = repositorySystem.resolve(req);
        final Iterator<Artifact> iter = result.getArtifacts().iterator();
        return iter.hasNext() ? iter.next() : null;
    }

    private void copyModel(JarFile jar, JarEntry entry, String basePath, String type, String targetDir) throws IOException {
        final String sourceName = (basePath.length() > 0 ? basePath + "/" : "") + type;
        if (!entry.isDirectory() && entry.getName().startsWith(sourceName)) {
            final File modelFile = apiDependencies(type + "/" + targetDir + entry.getName().substring((sourceName).length()));
            modelFile.getParentFile().mkdirs();
            try (final FileOutputStream out = new FileOutputStream(modelFile)) {
                IoUtils.copy(jar.getInputStream(entry), out);
            }
        }
    }

    private void updateJson(File file, Consumer<Map<String, Object>> updater) throws IOException {
        final ObjectMapper mapper = new ObjectMapper();
        final Map<String, Object> json = file.exists() ? mapper.readValue(file, Map.class) : new HashMap<>();
        updater.accept(json);
        mapper.writer().withDefaultPrettyPrinter().writeValue(file, json);
    }

    protected File file(String name) {
        return new File(mavenProject.getBasedir(), name);
    }

    protected File target(String name) {
        return new File(mavenProject.getBuild().getDirectory(), name);
    }

    protected File working(String name) {
        return new File(nodeWorkingDir, name);
    }

    protected File apiDependencies(String name) {
        return target("api-dependencies/" + name);
    }

    protected String relative(File base, File f) {
        return base.toPath().relativize(f.toPath()).toString().replace('\\', '/');
    }

    protected void writeProjectProps() throws IOException {
        final Map<String, Object> propectProps = new ProjectSerializer().serialize(mavenProject);
        final File file = working("properties.json");
        file.getParentFile().mkdirs();
        new ObjectMapper().writeValue(file, propectProps);
    }

    protected boolean isPom() {
        return "pom".equals(mavenProject.getPackaging());
    }

    protected void generatePackageJson(String version) throws IOException {
        updateJson(working("package.json"), pack -> {
            pack.put("name", mavenProject.getArtifactId());
            pack.put("version", mavenProject.getVersion());
            final Map<String, String> scripts = (Map) pack.merge("scripts", new HashMap<>(), (oldVal, newVal) -> oldVal);
            scripts.put("apikana", "apikana");
            final Map<String, String> devDependencies = (Map) pack.merge("devDependencies", new HashMap<>(), (oldVal, newVal) -> oldVal);
            devDependencies.put("apikana", version);
        });
    }

    protected void checkNodeInstalled() throws MojoExecutionException {
        try {
            Process node = new ProcessBuilder("node", "-v").start();
            if (node.waitFor() != 0) {
                throw new IOException();
            }
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException("Node is not installed on this machine.\n" +
                    "- Set <global>false</false> in this plugin's <configuration> or\n" +
                    "- Install node (https://docs.npmjs.com/getting-started/installing-node)");
        }
    }

    protected ProcessBuilder shellCommand(File workDir, String cmd) {
        getLog().info("Workdir: " + workDir);
        getLog().info("Executing: " + cmd);
        final ProcessBuilder pb = System.getProperty("os.name").toLowerCase().contains("windows")
                ? new ProcessBuilder("cmd", "/c", cmd) : new ProcessBuilder("bash", "-c", cmd);
        return pb.directory(workDir);
    }

    protected void executeFrontend(String goal, Xpp3Dom config) throws MojoExecutionException {
        final File npmrc = file(".npmrc");
        final String rc = npmrc.exists() ? "--userconfig " + npmrc.getAbsolutePath() + " " : "";
        config.addChild(element("workingDirectory", working("").getAbsolutePath()).toDom());
        final Xpp3Dom arguments = config.getChild("arguments");
        if (arguments != null) {
            arguments.setValue(rc + arguments.getValue());
        }
        execute(frontendPlugin(), goal, config);
    }

    private Plugin frontendPlugin() {
        return plugin("com.github.eirslett", "frontend-maven-plugin", "1.3");
    }

    private void execute(Plugin plugin, String goal, Xpp3Dom config) throws MojoExecutionException {
        executeMojo(plugin, goal, config, executionEnvironment(mavenProject, mavenSession, pluginManager));
    }
}
