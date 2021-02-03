/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.crates.local

import org.rust.RsTestBase

class CratesLocalIndexServiceTest : RsTestBase() {
    lateinit var cratesService: CratesLocalIndexService

    override fun setUp() {
        super.setUp()

        cratesService = CratesLocalIndexService.getInstance()
        cratesService.updateIfNeeded()

        while (!cratesService.isReady()) {
            Thread.sleep(10)
        }
    }

    fun `test service is ready`() {
        assertTrue(cratesService.isReady())
    }

    fun `test index has many crates`() {
        assertTrue(cratesService.getAllCrateNames().size > 50.000)
    }

    fun `test index has tokio`() {
        assertNotNull(cratesService.getCrate("tokio"))
    }

    fun `test tokio first published version`() {
        assertEquals(
            cratesService.getCrate("tokio")?.versions?.get(0)?.version,
            "0.0.0"
        )
    }

    fun `test tokio version is yanked`() {
        assertTrue(
            cratesService.getCrate("tokio")
                ?.versions
                ?.find { it.version == "1.0.0" }
                ?.isYanked == true
        )
    }

    fun `test tokio features`() {
        assertEquals(
            cratesService.getCrate("tokio")
                ?.versions
                ?.find { it.version == "1.0.0" }
                ?.features,
            listOf("io-util", "process", "macros", "rt", "io-std", "sync", "fs", "rt-multi-thread", "default", "test-util", "time", "net", "signal", "full")
        )
    }
}
