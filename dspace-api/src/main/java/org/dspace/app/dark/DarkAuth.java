/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.dark;

import java.io.PrintStream;

import org.dspace.identifier.dark.DarkAuthenticationService;
import org.dspace.identifier.dark.DarkIdentifierException;
import org.dspace.utils.DSpace;

/** Local CLI for protected key generation, sharing its storage routine with the administration API. */
public final class DarkAuth {

    private DarkAuth() { }

    /** Generates a pair without replacing existing keys. Successful output contains only the public key. */
    public static void main(String[] args) {
        if (args.length != 1 || !"--generate".equals(args[0])) {
            System.err.println("Usage: bin/dspace dark-auth --generate");
            System.exit(1);
            return;
        }
        try {
            DarkAuthenticationService authentication =
                new DSpace().getSingletonService(DarkAuthenticationService.class);
            int result = generate(authentication, System.out, System.err);
            if (result != 0) {
                System.exit(result);
            }
        } catch (RuntimeException e) {
            System.err.println("Unable to initialize dARK key generation.");
            System.exit(1);
        }
    }

    static int generate(DarkAuthenticationService authentication, PrintStream output, PrintStream errors) {
        try {
            output.println(authentication.generate());
            return 0;
        } catch (DarkIdentifierException | RuntimeException e) {
            // Never print parser/provider exceptions or stack traces from the key generation path.
            errors.println("dARK key generation failed; check the server-only directory and POSIX permissions."
                + " Existing keys are never overwritten.");
            return 1;
        }
    }
}
