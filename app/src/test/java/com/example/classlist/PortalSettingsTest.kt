package com.example.classlist

import com.example.classlist.data.PortalSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortalSettingsTest {
    @Test
    fun allowsOnlyTheThreeHttpsPortalHosts() {
        assertTrue(PortalSettings.isAllowed("https://yjs.cumtb.edu.cn/"))
        assertTrue(PortalSettings.isAllowed("https://sso.cumtb.edu.cn/login?service=test"))
        assertTrue(PortalSettings.isAllowed("https://yjsglxt.cumtb.edu.cn/Gstudent/Default.aspx"))
        assertFalse(PortalSettings.isAllowed("http://yjsglxt.cumtb.edu.cn/"))
        assertFalse(PortalSettings.isAllowed("https://example.com/"))
    }

    @Test
    fun recognizesWebsiteEntryAndImportPages() {
        assertTrue(PortalSettings.isWebsiteHome(PortalSettings.HOME_URL))
        assertTrue(PortalSettings.isOfficialEntry("https://yjsglxt.cumtb.edu.cn/ULogin.aspx?ticket=temporary"))
        assertFalse(PortalSettings.isOfficialEntry("http://yjsglxt.cumtb.edu.cn/ULogin.aspx?ticket=temporary"))
        assertFalse(PortalSettings.isOfficialEntry("https://yjsglxt.cumtb.edu.cn/ULogin.aspx?other=value"))
        assertTrue(PortalSettings.canImport("https://yjsglxt.cumtb.edu.cn/Gstudent/Default.aspx?UID=test"))
        assertFalse(PortalSettings.canImport("https://sso.cumtb.edu.cn/login"))
    }
}
