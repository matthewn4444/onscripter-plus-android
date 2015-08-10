package jrummy.sdfix;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.text.TextUtils;
import android.util.Log;

/**
 * 
 * @author Jared Rummler
 *
 */
public class Remounter {

	private static final String TAG = Remounter.class.getName();
	/** /proc/mounts File */
	public static final File PROC_MOUNTS = new File("/proc/mounts");
	/** Value of ANDROID_ROOT environmental variable or /system if empty */
	public static final String ANDROID_ROOT = System.getenv("ANDROID_ROOT");
	/** Read/Write "rw" */
	public static final String RW = "rw";
	/** Read-only "ro" */
	public static final String RO = "ro";

	/**
	 * Contains info extracted from a line in /proc/mounts
	 */
	public static class Mount {
		/** The line in /proc/mounts */
		public String line;
		public String device;
		public String mountpoint;
		public String type;
		public String options;
		public String mountOption;

		/**
		 * 
		 * @param line A line in /proc/mounts
		 * @throws IndexOutOfBoundsException
		 * @throws NullPointerException
		 */
		public Mount(String line) throws IndexOutOfBoundsException, NullPointerException {
			final String[] arr = line.split("\\s+");
			this.line = line;
			this.device = arr[0];
			this.mountpoint = arr[1];
			this.type = arr[2];
			this.options = arr[3];
			this.mountOption = this.options.split(",")[0];
		}

		/**
		 * @return <code>true</code> if the filesystem is mounted read-only.
		 */
		public boolean isMountedReadOnly() {
			if (this.mountOption != null) {
				return this.mountOption.equals(RO);
			}
			return false;
		}

		/**
		 * @return <code>true</code> if the filesystem is mounted read/write.
		 */
		public boolean isMountedReadWrite() {
			if (this.mountOption != null) {
				return this.mountOption.equals(RW);
			}
			return false;
		}

		/**
		 * @return The command used to remount the filesystem read/write.
		 */
		public String getMountCommand() {
			return getMountCommand(RW);
		}

		/**
		 * 
		 * @param mountType "ro" or "rw"
		 * @return The command used to remount the filesystem read/write or read-only
		 */
		public String getMountCommand(String mountType) {
			return String.format("mount -o remount,%s %s %s", 
					mountType, this.device, this.mountpoint);
		}

		private void setMountOption(String type) {
			this.line.replaceAll(this.mountOption + ",", type + ",");
			this.options.replaceAll(this.mountOption + ",", type + ",");
			this.mountOption = type;
		}

		/**
		 * Mounts a filesystem read-only or read/write
		 * @param mountType "ro" or "rw"
		 * @return <code>true</code> if successful
		 */
		public boolean remount(String mountType) {
			String command = getMountCommand(mountType);
			if (Shell.SU.run(command).success()) {
				setMountOption(mountType);
				return true;
			}

			command = "busybox " + command;
			if (Shell.SU.run(command).success()) {
				setMountOption(mountType);
				return true;
			}

			return false;
		}

		/**
		 * Mounts a filesystem read/write
		 * @return <code>true</code> if successful
		 */
		public boolean remountReadWrite() {
			return remount(RW);
		}

		/**
		 * Mounts a filesystem read-only
		 * @return <code>true</code> if successful
		 */
		public boolean remountReadOnly() {
			return remount(RO);
		}
	}

	/**
	 * Parses /proc/mounts and returns a List of {@link Remounter.Mount}
	 * @return
	 */
	public static List<Mount> getMounts() {
		final List<Mount> mounts = new ArrayList<Mount>();
		if (PROC_MOUNTS.canRead()) {
			try {
				final BufferedReader br = new BufferedReader(new FileReader(PROC_MOUNTS));
				try {
					String line = null;
					while ((line = br.readLine()) != null) {
						try {
							mounts.add(new Mount(line));
						} catch (Exception ignore) { }
					}
				} finally {
					br.close();
				}
				if (!mounts.isEmpty()) {
					return mounts;
				}
			} catch (IOException e) {
				Log.e(TAG, "Failed reading " + PROC_MOUNTS, e);
				mounts.clear();
			}
		}

		final String command = "cat \"" + PROC_MOUNTS + "\"";
		final Shell.CommandResult r = Shell.SU.run(command);
		if (r.success() && !TextUtils.isEmpty(r.stdout)) {
			final String[] lines = r.stdout.split("\n");
			for (final String line : lines) {
				try {
					mounts.add(new Mount(line));
				} catch (Exception ignore) { }
			}
		}

		return mounts;
	}

	/**
	 * 
	 * @param file
	 * @return The filesystem in /proc/mounts for this file or <code>null</code>.
	 */
	public static Mount getMount(File file) {
		return getMount(file.getAbsolutePath());
	}

	/**
	 * 
	 * @param path
	 * @return The filesystem in /proc/mounts for this file or <code>null</code>.
	 */
	public static Mount getMount(String path) {
		final List<Mount> mounts = getMounts();
		if (mounts.isEmpty()) {
			return null;
		}

		while (path != null) {
			for (final Mount mount : mounts) {
				if (mount.mountpoint.equals(path)) {
					return mount;
				}
			}

			if (path.equals(File.separator)) {
				return null;
			}

			path = new File(path).getParent();
			if (path == null) {
				path = File.separator;
			}
		}

		return null;
	}

	/**
	 * Remounts a filesystem read/write or read-only
	 * @param path Path to the file
	 * @param type "rw" or "ro"
	 * @return <code>true</code> if successful
	 */
	public static boolean remount(String path, String type) {
		final Mount mount = getMount(path);
		if (mount == null) {
			Log.i(TAG, "Failed finding mountpoint for " + path);
			return false;
		}

		if (mount.mountOption.equals(type)) {
			// Already mounted
			return true;
		}

		return mount.remount(type);
	}

	/** @see #remount(String, String) */
	public static boolean remount(File file, String type) {
		return remount(file.getAbsolutePath(), type);
	}

	/** @see #remount(String, String) */
	public static boolean rw(String path) {
		return remount(path, RW);
	}

	/** @see #remount(String, String) */
	public static boolean ro(String path) {
		return remount(path, RO);
	}

	/** @see #remount(String, String) */
	public static boolean rw(File file) {
		return remount(file, RW);
	}

	/** @see #remount(String, String) */
	public static boolean ro(File file) {
		return remount(file, RO);
	}

	/** @see #remount(String, String) */
	public static boolean mountSystemRw() {
		return rw(ANDROID_ROOT);
	}

	/** @see #remount(String, String) */
	public static boolean mountSystemRo() {
		return ro(ANDROID_ROOT);
	}
}
