package com.mumfrey.mcptools;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.common.io.LineProcessor;
import com.google.common.io.Resources;

/**
 * GLStaticPatcher is a simple utility for restoring the LWJGL static imports in MCP source code
 * 
 * @author Adam Mummery-Smith
 */
public class GLStaticPatcher implements FilenameFilter
{
	/**
	 * Version string
	 */
	public static final String VERSION = "1.1.0";
	
	/**
	 * Pattern used to represent regular imports that we want to match
	 */
	public static final Pattern importLinePattern = Pattern.compile("import (org\\.lwjgl\\.(?:opengl|util\\.glu)\\.(Project|GL\\w+));");
	
	/**
	 * Base directory to operate from, we recursively walk all subdirectories below here
	 */
	private final File baseDir;
	
	/**
	 * Some method calls need to be excluded from transformation to prevent certain issues, for example when a method containing a
	 * call to an LWJGL OpenGL method has the same name as the method being invoked (leading to stack overflow)
	 */
	private final ExclusionList exclusions = new ExclusionList();
	
	private long startTime = System.currentTimeMillis();
	
	private int processedFiles = 0;
	private int failedFiles = 0;
	
	/**
	 * @param baseDir base directory, iterates all subdirectories of the dir specified
	 * @param excludesFile 
	 * @throws IOException
	 */
	public GLStaticPatcher(File baseDir, File excludesFile) throws IOException
	{
		this.baseDir = baseDir.getCanonicalFile();
		
		if (!this.exclusions.loadFrom(excludesFile))
		{
			this.exclusions.loadFrom(Resources.getResource(GLStaticPatcher.class, "/defaultExcludes.txt"));
		}
	}
	
	public ExclusionList getExclusions()
	{
		return this.exclusions;
	}

	public void run()
	{
		System.out.printf("Running in %s\n", baseDir.getAbsolutePath());
		
		this.startTime = System.currentTimeMillis();
		this.processDir(this.baseDir);
		
		System.out.printf("Patch completed, patched %d file(s) with %d failure(s) in %d ms\n", this.processedFiles, this.failedFiles, System.currentTimeMillis() - this.startTime); 
	}
	
	/* (non-Javadoc)
	 * @see java.io.FilenameFilter#accept(java.io.File, java.lang.String)
	 */
	@Override
	public boolean accept(File dir, String name)
	{
		return dir.isDirectory() || (dir.isFile() && name.endsWith(".java"));
	}

	/**
	 * Recursive function to process a directory, calls processFile() for each file and recursively calls
	 * itself for each directory
	 * 
	 * @param dir
	 */
	private void processDir(File dir)
	{
		for (File file : dir.listFiles(this))
		{
			if (file.isDirectory())
				this.processDir(file);
			else
				this.processFile(file);
		}
	}

	/**
	 * Process a file
	 * 
	 * @param file
	 */
	private void processFile(File file)
	{
		// Pre-scan finds exclusion mappings in the file
		final Map<String, String> fullyQualifiedEntries = this.preScanFile(file);
		
		// Transform makes the changes to the file
		this.transformFile(file, fullyQualifiedEntries);
	}

	/**
	 * Scan a file and generate fully qualified versions of any excluded methods, this method scans the import list
	 * and builds a map of package names to fully qualified package names (eg. GL11 => org.lwjgl.opengl.GL11) and then
	 * scans the file for any qualified method calls and adds entries to the returned map.
	 * 
	 * @param file
	 * @return
	 */
	public Map<String, String> preScanFile(final File file)
	{
		final Map<String, String> fullyQualifiedEntries = new HashMap<String, String>();
		
		if (this.exclusions.hasExcludesForFile(file))
		{		
			try
			{
				Files.readLines(file, Charsets.UTF_8, new LineProcessor<String>()
				{
					private Map<String, String> fullyQualifiedPackageNames = new HashMap<String, String>();
					
					private Map<String, Pattern> methodPatterns = new HashMap<String, Pattern>();

					private ExclusionList exclusionsList = GLStaticPatcher.this.getExclusions();
					
					@Override
					public String getResult()
					{
						return null;
					}
	
					@Override
					public boolean processLine(String line) throws IOException
					{
						Matcher importLinePatternMatcher = GLStaticPatcher.importLinePattern.matcher(line);
						while (importLinePatternMatcher.find())
						{
							this.fullyQualifiedPackageNames.put(importLinePatternMatcher.group(2) + ".", importLinePatternMatcher.group(1) + ".");
							this.methodPatterns.put(importLinePatternMatcher.group(2) + ".", Pattern.compile("(" + importLinePatternMatcher.group(2) + "\\.(gl[a-z0-9]+))\\(", Pattern.CASE_INSENSITIVE));
						}

						for (Entry<String, Pattern> methodPattern : this.methodPatterns.entrySet())
						{
							Matcher importPatternMatcher = methodPattern.getValue().matcher(line);
							while (importPatternMatcher.find())
							{
								if (this.exclusionsList.shouldExcludeMethod(file, importPatternMatcher.group(2)))
								{
									String packageName = this.fullyQualifiedPackageNames.get(methodPattern.getKey());
									fullyQualifiedEntries.put(importPatternMatcher.group(1), packageName + importPatternMatcher.group(2));
								}
							}
						}
						
						return true;
					}
				});
			}
			catch (IOException ex)
			{
				ex.printStackTrace();
			}
		}
		
		return fullyQualifiedEntries;
	}

	/**
	 * @param file
	 * @param fullyQualifiedEntries
	 */
	public void transformFile(final File file, final Map<String, String> fullyQualifiedEntries)
	{
		try
		{
			String contents = Files.readLines(file, Charsets.UTF_8, new LineProcessor<String>()
			{
				private StringBuilder stringBuilder = new StringBuilder();
				
				private Set<String> imports = new HashSet<String>();
				
				@Override
				public String getResult()
				{
					return this.stringBuilder.toString();
				}

				@Override
				public boolean processLine(String line) throws IOException
				{
					Matcher importLinePatternMatcher = GLStaticPatcher.importLinePattern.matcher(line);
					while (importLinePatternMatcher.find())
					{
						this.imports.add(String.format("(?<!\\.)%s\\.(?=[a-zA-Z])", importLinePatternMatcher.group(2)));
						String staticImport = String.format("import static %s.*;", importLinePatternMatcher.group(1));
						line = line.substring(0, importLinePatternMatcher.start()) + staticImport + line.substring(importLinePatternMatcher.end());
						importLinePatternMatcher.reset(line);
					}

					for (Entry<String, String> fullyQualifiedEntry : fullyQualifiedEntries.entrySet())
						line = line.replace(fullyQualifiedEntry.getKey(), fullyQualifiedEntry.getValue());
					
					for (String imp : this.imports)
						line = line.replaceAll(imp, "");
					
					this.stringBuilder.append(line).append("\r\n");
					return true;
				}
			});
			
			Files.write(contents, file, Charsets.UTF_8);
			this.processedFiles++;
		}
		catch (IOException ex)
		{
			ex.printStackTrace();
			this.failedFiles++;
		}
	}

	public static void main(String[] args)
	{
		System.out.println("GLStaticPatcher version " + GLStaticPatcher.VERSION);
		
		try
		{
			if (args.length > 0)
			{
				File baseDir = new File(args[0]);
				File excludesFile = null;
				
				if (args.length > 1)
				{
					excludesFile = baseDir;
					baseDir = new File(args[1]);
				}				
				
				if (baseDir.exists() && baseDir.isDirectory())
				{
					GLStaticPatcher patcher = new GLStaticPatcher(baseDir, excludesFile);
					patcher.run();
				}
				else
				{
					System.out.println("Error: " + baseDir.getAbsolutePath() + " does not exist or is not a directory");
					System.exit(1);
				}
			}
			else
			{
				System.out.println("Usage: java -jar GLStaticPatcher.jar <path>");
				System.out.println("       java -jar GLStaticPatcher.jar <excludefile> <path>");
			}
		}
		catch (IOException ex)
		{
			ex.printStackTrace();
			System.exit(1);
		}
	}
}
