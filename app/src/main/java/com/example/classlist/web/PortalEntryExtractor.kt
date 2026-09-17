package com.example.classlist.web

object PortalEntryExtractor {
    val script: String = """
        (function () {
          var links = document.querySelectorAll('a[href]');
          for (var index = 0; index < links.length; index++) {
            var label = (links[index].innerText || '').replace(/\s+/g, ' ').trim();
            var href = links[index].href || '';
            if (label === '研究生信息管理系统' &&
                /^https:\/\/yjsglxt\.cumtb\.edu\.cn\/ULogin\.aspx\?/i.test(href)) {
              return href;
            }
          }
          return '';
        })();
    """.trimIndent()
}
