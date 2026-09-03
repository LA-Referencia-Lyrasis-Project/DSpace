/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.dark;

import org.apache.commons.cli.Options;
import org.dspace.scripts.configuration.ScriptConfiguration;

/**
 * Script configuration for {@link DarkMint}.
 */
public class DarkMintScriptConfiguration extends ScriptConfiguration<DarkMint> {

    private Class<DarkMint> dspaceRunnableClass;

    @Override
    public Options getOptions() {
        if (options == null) {
            options = new Options();
            options.addOption("u", "uuid", true, "mint a dARK for one Item UUID");
            options.addOption("a", "all", false, "mint dARKs for all archived Items without one");
            options.addOption("h", "help", false, "help");
        }
        return options;
    }

    @Override
    public Class<DarkMint> getDspaceRunnableClass() {
        return dspaceRunnableClass;
    }

    @Override
    public void setDspaceRunnableClass(Class<DarkMint> dspaceRunnableClass) {
        this.dspaceRunnableClass = dspaceRunnableClass;
    }
}