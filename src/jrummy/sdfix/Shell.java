package jrummy.sdfix;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

/**
 * Shell.java runs commands as if in a native shell instance, and can
 * return stdout, stderr, and exit code.<br><br>
 * 
 * Example usage:<br>
 * <code>new Shell("sh").run("ls");</code>
 * 
 * @author Jared Rummler
 *
 */
public class Shell {

	private static final String TAG = "Shell";

	/** The LD_LIBRARY_PATH environmental variable value **/
	private static final String LD_LIBRARY_PATH = System.getenv("LD_LIBRARY_PATH");

	private static Boolean canSu;
	private String shell;

	private Handler mHandler;
	private CommandListener mListener;

	public interface CommandListener {
		public void onExecuteCommand(String command);
		public void onReadOutput(String line, boolean stdout);
		public void onFinished(CommandResult result);
	}

	/**
	 * Constructor to create a new shell to run linux commands in.
	 * 
	 * @param shell The name of the shell commands will be executed in
	 */
	public Shell(String shell) {
		this.shell = shell;
	}

	/**
	 * Constructor to create a new root shell to run linux commands in
	 */
	public Shell() {
		this("su");
	}

	public Shell setCommandListener(CommandListener listener) {
		return setCommandListener(null, listener);
	}

	public Shell setCommandListener(Handler handler, CommandListener listener) {
		mHandler = handler;
		mListener = listener;
		return this;
	}

	public static class SU {
		public static CommandResult run(String...commands) {
			return new Shell("su").run(commands);
		}
		public static CommandResult run(List<String> commands) {
			return run(commands.toArray(new String[]{}));
		}
	}

	public static class SH {
		public static CommandResult run(String...commands) {
			return new Shell("sh").run(commands);
		}
		public static CommandResult run(List<String> commands) {
			return run(commands.toArray(new String[]{}));
		}
	}

	public static Shell.CommandResult run(String shell, String[] commands) {
		return new Shell(shell).run(commands);
	}

	/**
	 * 
	 * @return The current shell commands are being executed in.
	 */
	public String getShell() {
		return shell;
	}

	/**
	 * Sets the current shell to execute commands in.
	 * @see #SU
	 * @see #SH
	 * @see #BASH
	 * @param shell
	 */
	public void setShell(String shell) {
		this.shell = shell;
	}

	/**
	 * Contains information about the last run command.
	 */
	public static class CommandResult {
		/** The exit value returned by the last run command. -1 by default. **/
		public int exitValue = -1;
		/** Standard output of all commands run **/
		public String stdout;
		/** Standard error by all commands run **/
		public String stderr;

		public CommandResult() {

		}

		public CommandResult(int exitValue, String stdout, String stderr) {
			this.exitValue = exitValue;
			this.stdout = stdout;
			this.stderr = stderr;
		}

		/**
		 * 
		 * @return <code>true</code> if the {@link #exitValue} is equal to 0.
		 */
		public boolean success() {
			return exitValue == 0;
		}

		public boolean hasOutput() {
			return !TextUtils.isEmpty(this.stdout);
		}

		public boolean isSuccess() {
			return success() && hasOutput();
		}
	}

	private void onReadLine(final String line, final boolean stdout) {
		if (mListener != null) {
			if (mHandler != null) {
				mHandler.post(new Runnable() {
					@Override
					public void run() {
						mListener.onReadOutput(line, stdout);
					}
				});
			} else {
				mListener.onReadOutput(line, stdout);
			}
		}
	}

	private String readStream(InputStreamReader stream, boolean stdout) {
		BufferedReader reader = new BufferedReader(stream);
		StringBuffer buffer = null;
		try {
			String line = reader.readLine();
			if (line != null) {
				buffer = new StringBuffer(line);
				while ((line = reader.readLine()) != null) {
					onReadLine(line, stdout);
					buffer.append("\n").append(line);
				}
			}
		} catch (IOException e) {
			Log.i(TAG, "Error: " + e.getMessage());
		}

		if (buffer != null) {
			return buffer.toString();
		}

		return null;
	}

	/**
	 * Runs commands in the current shell.
	 * 
	 * @param commands A single or an array of commands the current {@link #shell} should run.
	 * @return The result (see {@linkplain CommandResult}) of all the commands run.
	 */
	public CommandResult run(String... commands) {
		final CommandResult result = new CommandResult();
		InputStreamReader osStdout = null;
		InputStreamReader osStderr = null;
		DataOutputStream os = null;
		Process process = null;
		Runtime.getRuntime().gc();

		try {
			process = Runtime.getRuntime().exec(shell);
			os = new DataOutputStream(process.getOutputStream());
			osStdout = new InputStreamReader(process.getInputStream());
			osStderr = new InputStreamReader(process.getErrorStream());

			try {
				for (String command : commands) {

					if (shell.equals("su") && LD_LIBRARY_PATH != null) {
						// On some versions of Android (ICS) LD_LIBRARY_PATH is unset when using su
						// We need to pass LD_LIBRARY_PATH over su for some commands to work correctly.
						command = "LD_LIBRARY_PATH=" + LD_LIBRARY_PATH + " " + command;
					}

					if (mListener != null) {
						if (mHandler != null) {
							final String cmd = command;
							mHandler.post(new Runnable() {
								@Override
								public void run() {
									mListener.onExecuteCommand(cmd);
								}
							});
						} else {
							mListener.onExecuteCommand(command);
						}
					}

					os.writeBytes(command + "\n");
					os.flush();
				}

				os.writeBytes("exit\n");
				os.flush();

				result.stdout = readStream(osStdout, true);
				result.stderr = readStream(osStderr, false);
				result.exitValue = process.waitFor();
			} catch (InterruptedException e) {
				Log.i(TAG, "Error running commands: " + e.getMessage(), e);
			} catch (Exception e) {
				Log.i(TAG, "Error running commands: " + e.getMessage(), e);
			}
		} catch (IOException e) {
			Log.i(TAG, "Error: " + e.getMessage());
		} finally {
			try {
				if (os != null) {
					os.close();
				}
				if (osStdout != null) {
					osStdout.close();
				}
				if (osStderr != null) {
					osStderr.close();
				}
				if (process != null) {
					process.destroy();
				}
			} catch (Exception ignore) { }
		}

		if (mListener != null) {
			if (mHandler != null) {
				mHandler.post(new Runnable() {
					@Override
					public void run() {
						mListener.onFinished(result);
					}
				});
			} else {
				mListener.onFinished(result);
			}
		}

		return result;
	}

	/**
	 * Checks if the user is rooted<br>
	 * forceCheck defaults to <code>false</code>
	 * @see #canSu(boolean)
	 */
	public static boolean canSu() {
		return canSu(false);
	}

	/**
	 * Checks if the user has root access.
	 * @param forceCheck Set to <code>true</code> to check for root 
	 *                   regardless if it has already been checked.
	 * @return <code>true</code> if the user has root access.
	 */
	public static boolean canSu(boolean forceCheck) {
		if (canSu == null || forceCheck) {
			CommandResult r = SU.run("id");
			StringBuilder out = new StringBuilder();

			if (r.stdout != null) {
				out.append(r.stdout).append(" ; ");
			}
			if (r.stderr != null) {
				out.append(r.stderr);
			}
			Log.d(TAG, "canSU() su[" + r.exitValue + "]: " + out);
			canSu = r.success();
		}

		return canSu;
	}

}
