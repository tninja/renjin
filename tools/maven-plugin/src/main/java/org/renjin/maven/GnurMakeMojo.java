package org.renjin.maven;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.common.io.LineProcessor;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.renjin.gcc.Gcc;
import org.renjin.gcc.GimpleCompiler;
import org.renjin.gcc.HtmlTreeLogger;
import org.renjin.gcc.InternalCompilerException;
import org.renjin.gcc.gimple.GimpleCompilationUnit;
import org.renjin.gcc.gimple.GimpleFunction;
import org.renjin.gcc.gimple.GimpleParser;
import org.renjin.gcc.maven.GccBridgeHelper;
import org.renjin.gnur.GnurInstallation;
import org.renjin.gnur.GnurSourcesCompiler;

import javax.annotation.concurrent.ThreadSafe;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Builds a package written for GNU R
 */
@ThreadSafe
@Mojo(name = "make-gnur-sources", requiresDependencyCollection = ResolutionScope.COMPILE)
public class GnurMakeMojo extends AbstractMojo {

  private static final List<String> SOURCE_EXTENSIONS = Lists.newArrayList("c", "f", "f77", "cpp", "cxx");


  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project;

  @Component
  private MavenProjectHelper projectHelper;

  @Parameter(defaultValue = "${plugin.artifacts}", readonly = true)
  private List<Artifact> pluginDependencies;

  @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true)
  private File outputDirectory;

  /**
   * Directory to which the intermediate gimple files are written
   */
  @Parameter(defaultValue = "${project.build.directory}/gimple", required = true)
  private File gimpleDirectory;

  /**
   * Directories in which to look for C/Fortran sources
   */
  @Parameter(defaultValue = "${project.basedir}/src")
  private File nativeSourceDir;

  @Parameter(defaultValue = "${project.build.directory}/staging")
  private File stagingDir;
  
  /**
   * If true, do not fail the build if compilation fails.
   */
  @Parameter(property = "ignore.gnur.compilation.failure", defaultValue = "false")
  private boolean ignoreFailure;

  /**
   * Scratch directory for GCC output/files
   */
  @Parameter(defaultValue = "${project.build.directory}/gcc-work")
  private File workDirectory;

  @Parameter
  private List<File> includeDirectories;

  @Parameter(defaultValue = "${project.build.finalName}")
  private String finalName;

  @Parameter(defaultValue = "${project.build.directory}/include", readonly = true)
  private File unpackedIncludeDir;

  @Parameter(defaultValue = "${project.build.directory}/gnur", readonly = true)
  private File homeDir;
  
  @Parameter(defaultValue = "${project.build.directory}/bridge.so", readonly = true)
  private File pluginFile;

  @Parameter(defaultValue = "${project.build.directory}/gcc-bridge-logs")
  private File logDir;
  
  @Parameter(defaultValue = "false", property = "gcc.bridge.logging")
  private boolean loggingEnabled;
  
  @Parameter
  private List<String> entryPoints;
  

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {

    if(nativeSourceDir.exists()) {
      try {
        setupGnurInstallation();
        setupStagingDir();
        make();
        compileGimple();
        getLog().info("Compilation of GNU R sources succeeded.");

      } catch (InterruptedException e) {
        throw new MojoExecutionException("Interrupted");

      } catch (Exception e) {
        if (ignoreFailure) {
          getLog().error("Compilation of GNU R sources failed.");
          e.printStackTrace(System.err);
        } else {
          throw new MojoExecutionException("Compilation of GNU R sources failed", e);
        }
      }
    }

    archiveHeaders();
  }

  private void setupGnurInstallation() throws MojoExecutionException, IOException {
    // Unpack any headers from dependencies
    GccBridgeHelper.unpackHeaders(getLog(), unpackedIncludeDir, project.getCompileArtifacts());
    GnurInstallation.unpackRHome(homeDir);
    Gcc.extractPluginTo(pluginFile);
  }


  private void setupStagingDir() throws IOException {
    if(!stagingDir.exists()) {
      stagingDir.mkdirs();
    }
    
    Files.copy(new File(project.getBasedir(), "DESCRIPTION"), new File(stagingDir, "DESCRIPTION"));
    Files.copy(new File(project.getBasedir(), "NAMESPACE"), new File(stagingDir, "NAMESPACE"));
  }

  private void make() throws IOException, InterruptedException {
    
    File makeconfFile = new File(homeDir.getAbsolutePath() + "/etc/Makeconf");
    File shlibMk = new File(homeDir.getAbsolutePath() + "/share/make/shlib.mk");
    
    List<String> commandLine = Lists.newArrayList();
    commandLine.add("make");

    // Combine R's default Makefile with package-specific Makevars if present
    File makevars = new File(nativeSourceDir, "Makevars");
    if (makevars.exists()) {
      commandLine.add("-f");
      commandLine.add("Makevars");
    }

    // Makeconf file
    commandLine.add("-f");
    commandLine.add(makeconfFile.getAbsolutePath());
    
    commandLine.add("-f");
    commandLine.add(shlibMk.getAbsolutePath());

    commandLine.add("SHLIB='dummy.so'");
    
    if(!objectsDefinedByMakeVars(makevars)) {
      commandLine.add("OBJECTS=" + findObjectFiles());
    }
    commandLine.add("BRIDGE_PLUGIN=" + pluginFile);

    getLog().debug("Executing " + Joiner.on(" ").join(commandLine));

    // Setup process
    ProcessBuilder builder = new ProcessBuilder()
        .command(commandLine)
        .directory(nativeSourceDir)
        .inheritIO();

    builder.environment().put("R_VERSION", "3.2.0");
    builder.environment().put("R_HOME", homeDir.getAbsolutePath());
    builder.environment().put("R_INCLUDE_DIR", homeDir.getAbsolutePath() + "/include");
    builder.environment().put("R_SHARE_DIR", homeDir.getAbsolutePath() + "/share");

    builder.environment().put("R_PACKAGE_NAME", project.getArtifactId());
    builder.environment().put("R_INSTALL_PACKAGE", project.getArtifactId());
    builder.environment().put("R_PACKAGE_DIR", stagingDir.getAbsolutePath());
    
    builder.environment().put("CLINK_CPPFLAGS", "-I\"" + unpackedIncludeDir.getAbsolutePath() + "\"");
    
    // Provide sensible defaults for locations of system tools
    if(!builder.environment().containsKey("MAKE")) {
      builder.environment().put("MAKE", "make");
    }
    
    if(!builder.environment().containsKey("R_UNZIPCMD")) {
      builder.environment().put("R_UNZIPCMD", "/usr/bin/unzip");
    }
    
    if(!builder.environment().containsKey("R_GZIPCMD")) {
      builder.environment().put("R_GZIPCMD", "/usr/bin/gzip");
    }
    
    int exitCode = builder.start().waitFor();
    if (exitCode != 0) {
      throw new InternalCompilerException("Failed to execute Makefile");
    }
  }

  private void compileGimple() throws MojoExecutionException {

    List<GimpleCompilationUnit> gimpleFiles = Lists.newArrayList();
    collectGimple(nativeSourceDir, gimpleFiles);
    
    GimpleCompiler compiler = new GimpleCompiler();
    compiler.setLinkClassLoader(GccBridgeHelper.getLinkClassLoader(project, getLog()));
    compiler.setOutputDirectory(outputDirectory);
    compiler.setPackageName(project.getGroupId() + "." + project.getArtifactId());
    compiler.setClassName(project.getArtifactId());
    
    if(entryPoints != null && !entryPoints.isEmpty()) {
      compiler.setEntryPointPredicate(new Predicate<GimpleFunction>() {
        @Override
        public boolean apply(GimpleFunction input) {
          return entryPoints.contains(input.getMangledName());
        }
      });
    }
    
    if(loggingEnabled) {
      compiler.setLogger(new HtmlTreeLogger(logDir));
    }

    try {
      GnurSourcesCompiler.setupCompiler(compiler);
    } catch (ClassNotFoundException e) {
      throw new MojoExecutionException("Failed to setup Gimple Compiler", e);
    }

    try {
      compiler.compile(gimpleFiles);
    } catch (Exception e) {
      throw new MojoExecutionException("Failed to compile Gimple", e);
    }
  }

  private void archiveHeaders() throws MojoExecutionException {
    File instDir = new File(project.getBasedir(), "inst");
    File includeDir = new File(instDir, "include");
    
    // Some packages copy or create headers here as part of the 
    // build process
    File stagingIncludes = new File(stagingDir, "include");
    
    if(includeDir.exists() || stagingIncludes.exists()) {
      GccBridgeHelper.archiveHeaders(getLog(), project, includeDir, stagingIncludes);
    }
  }


  private boolean objectsDefinedByMakeVars(File makevars) throws IOException {
    if(!makevars.exists()) {
      return false;
    }

    final Pattern definitionRegexp = Pattern.compile("^OBJECTS\\s*=");
    
    return Files.readLines(makevars, Charsets.UTF_8, new LineProcessor<Boolean>() {
      
      private boolean defined = false;
      
      @Override
      public boolean processLine(String line) throws IOException {
        if(definitionRegexp.matcher(line).find()) {
          defined = true;
          return false;
        }
        // Keep processing
        return true;
      }

      @Override
      public Boolean getResult() {
        return defined;
      }
    });
  }
  
  private String findObjectFiles() {
    List<String> objectFiles = new ArrayList<>();
    File[] files = nativeSourceDir.listFiles();
    if(files != null)  {
      for (File file : files) {
        String extension = Files.getFileExtension(file.getName());
        if(SOURCE_EXTENSIONS.contains(extension)) {
          String baseName = Files.getNameWithoutExtension(file.getName());
          objectFiles.add(baseName + ".o");
        }
      }
    }
    return Joiner.on(" ").join(objectFiles);
  }

  private void collectGimple(File dir, List<GimpleCompilationUnit> gimpleFiles) throws MojoExecutionException {
    
    GimpleParser parser = new GimpleParser();
    
    File[] files = dir.listFiles();
    if(files != null) {
      for (File file : files) {
        if(file.getName().endsWith(".gimple")) {
          getLog().debug("Reading " + file.getAbsolutePath().substring(nativeSourceDir.getAbsolutePath().length()));
          try {
            gimpleFiles.add(parser.parse(file));
          } catch (IOException e) {
            throw new MojoExecutionException("Failed to parse gimple file " + file, e);
          }
        } else if(file.isDirectory()) {
          collectGimple(file, gimpleFiles);
        }
      }
    }
  }

}
