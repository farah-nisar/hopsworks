package se.kth.hopsworks.zeppelin.util;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.VFS;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import se.kth.bbc.project.Project;
import se.kth.bbc.project.ProjectFacade;
import se.kth.hopsworks.zeppelin.server.ZeppelinConfigFactory;

@Stateless
public class ZeppelinResource {

  private static final Logger logger
          = Logger.getLogger(ZeppelinResource.class.getName());

  @EJB
  private ProjectFacade projectBean;
  @EJB
  private ZeppelinConfigFactory zeppelinConfFactory;

  public ZeppelinResource() {
  }

  /**
   * Checks if an interpreter is running
   * can return false if pid file reading fails.
   * <p/>
   * @param interpreter
   * @param project
   * @return
   */
  public boolean isInterpreterRunning(InterpreterSetting interpreter, Project project) {
    FileObject[] pidFiles;
    try {
      pidFiles = getPidFiles(project);
    } catch (URISyntaxException | FileSystemException ex) {
      logger.log(Level.SEVERE, "Could not read pid files ", ex);
      return false;
    }
    boolean running = false;

    for (FileObject file : pidFiles) {
      if (file.getName().toString().contains(interpreter.getGroup())) {
        running = isProccessAlive(readPid(file));
        //in the rare case were there are more that one pid files for the same 
        //interpreter break only when we find running one
        if (running) {
          break;
        }
      }
    }
    return running;
  }

  private FileObject[] getPidFiles(Project project) throws URISyntaxException,
          FileSystemException {
    ZeppelinConfiguration conf = zeppelinConfFactory.getZeppelinConfig(
            project.getName()).getConf();
    URI filesystemRoot;
    FileSystemManager fsManager;
    String runPath = conf.getRelativeDir("run");//the string run should be a constant.
    try {
      filesystemRoot = new URI(runPath);
    } catch (URISyntaxException e1) {
      throw new URISyntaxException("Not a valid URI", e1.getMessage());
    }

    if (filesystemRoot.getScheme() == null) { // it is local path
      try {
        filesystemRoot = new URI(new File(runPath).getAbsolutePath());
      } catch (URISyntaxException e) {
        throw new URISyntaxException("Not a valid URI", e.getMessage());
      }
    }
    FileObject[] pidFiles = null;
    try {
      fsManager = VFS.getManager();
//      pidFiles = fsManager.resolveFile(filesystemRoot.toString() + "/").
      pidFiles = fsManager.resolveFile(filesystemRoot.getPath()).getChildren();
    } catch (FileSystemException ex) {
      throw new FileSystemException("Directory not found: " + filesystemRoot.
              getPath(), ex.getMessage());
    }
    return pidFiles;
  }

  /**
   * Retrieves projectId from cookies and returns the project associated with the id.
   * @param request
   * @return 
   */
  public Project getProjectNameFromCookies(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    String projectId = null;
    Integer pId;
    Project project;
    if (cookies != null) {
      for (int i = 0; i < cookies.length; i++) {
        if (cookies[i].getName().equals("projectID")) {
          projectId = cookies[i].getValue();
          break;
        }
      }
    }
    try {
      pId = Integer.valueOf(projectId);
      project = projectBean.find(pId);
    } catch (NumberFormatException e) {
      return null;
    }
    return project;
  }

  private boolean isProccessAlive(String pid) {

    logger.log(Level.INFO,
            "Checking if Zeppelin Interpreter alive with PID: {0}", pid);
    String[] command = {"kill", "-0", pid};
    ProcessBuilder pb = new ProcessBuilder(command);
    if (pid == null) {
      return false;
    }

    //TODO: We should clear the environment variables before launching the 
    // redirect stdout and stderr for child process to the zeppelin/project/logs file.
    int exitValue;
    try {
      Process p = pb.start();
      p.waitFor();
      exitValue = p.exitValue();
    } catch (IOException | InterruptedException ex) {

      logger.log(Level.WARNING, "Problem testing Zeppelin Interpreter: {0}", ex.
              toString());
      //if the pid file exists but we can not test if it is alive then
      //we answer true, b/c pid files are deleted when a process is killed.
      return true;
    }
    return exitValue == 0;
  }

  private String readPid(FileObject file) {
    //pid value can only be extended up to a theoretical maximum of 
    //32768 for 32 bit systems or 4194304 for 64 bit:
    byte[] pid = new byte[8];
    try {
      file.getContent().getInputStream().read(pid);
    } catch (FileSystemException ex) {
      return null;
    } catch (IOException ex) {
      return null;
    }
    String s;
    try {
      s = new String(pid, "UTF-8").trim();
    } catch (UnsupportedEncodingException ex) {
      return null;
    }
    return s;
  }
}
