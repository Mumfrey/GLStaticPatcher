GLStaticPatcher
===============

**GLStaticPatcher** is a very simple tool designed for use with [Minecraft Coder Pack](http://mcp.ocean-labs.de/) which restores the LWJGL OpenGL static imports to the source code.

It takes a single argument which is the base directory of the source tree to transform and can be easily integrated into MCP with some simple modifications to the python source.

Exclusions
----------

The first version of **GLStaticPatcher** would indiscriminately patch all references in all files, however I realised that in MCP's *TextureUtil* class this resulted in the following:

    public static int glGenTextures()
    {
        return glGenTextures();
    }

To avoid this I added an exclusion file which contains the names of gl methods to fully qualify instead of accessing statically, this leads to the following transformation being applied to the method calls in question:

    public static int glGenTextures()
    {
        return org.lwjgl.opengl.GL11.glGenTextures();
    }

Exclusions take the form

    SourceFileName.java/glMethodName
    
and are entered one per line in the excludes file.