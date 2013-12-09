package com.mumfrey.mcptools;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.common.io.Resources;

/**
 * Collection of methods in specific files to fully qualify instead of static importing
 * 
 * @author Adam Mummery-Smith
 */
public class ExclusionList
{
	/**
	 * An exclusion file entry looks like this:
	 * 
	 *   FileName.java/glExampleMethod
	 */
	private static final Pattern excludeLinePattern = Pattern.compile("^([a-z_$][a-z\\d_$]*\\.java)/(gl[a-z0-9]+)$", Pattern.CASE_INSENSITIVE);
	
	/**
	 * Compiled map of excludes
	 */
	private final Map<String, Set<String>> excludes = new HashMap<String, Set<String>>();
	
	public void clear()
	{
		this.excludes.clear();
	}
	
	/**
	 * Load exclusion list from a file
	 * 
	 * @param excludesFile
	 * @return 
	 */
	public boolean loadFrom(File excludesFile)
	{
		try
		{
			if (excludesFile != null && excludesFile.exists())
			{
				this.readExcludeLines(Files.readLines(excludesFile, Charsets.UTF_8));
				return true;
			}
		}
		catch (IOException ex) {}
		
		return false;
	}

	/**
	 * Load exclusion list from a file
	 * 
	 * @param excludesFile
	 * @return 
	 */
	public void loadFrom(URL resource)
	{
		try
		{
			this.readExcludeLines(Resources.readLines(resource, Charsets.UTF_8));
		}
		catch (IOException ex) {}
	}
	
	/**
	 * @param lines
	 */
	public void readExcludeLines(List<String> lines)
	{
		if (lines == null) return;
		
		for (String line : lines)
		{
			Matcher excludeLinePatternMatcher = ExclusionList.excludeLinePattern.matcher(line.trim());
			if (excludeLinePatternMatcher.matches())
			{
				String excludeFile = excludeLinePatternMatcher.group(1);
				String excludeMethod = excludeLinePatternMatcher.group(2);
				this.addExclusion(excludeFile, excludeMethod);
			}
		}
	}

	/**
	 * @param excludeFile
	 * @param excludeMethod
	 */
	public void addExclusion(String excludeFile, String excludeMethod)
	{
		if (!this.excludes.containsKey(excludeFile))
			this.excludes.put(excludeFile, new HashSet<String>());
		
		this.excludes.get(excludeFile).add(excludeMethod);
	}
	
	public boolean hasExcludesForFile(File file)
	{
		return file != null && this.excludes.containsKey(file.getName());
	}
	
	public boolean shouldExcludeMethod(File file, String method)
	{
		if (file == null) return false;
		Set<String> fileExcludes = this.excludes.get(file.getName());
		return (fileExcludes != null && fileExcludes.contains(method));
	}
}
