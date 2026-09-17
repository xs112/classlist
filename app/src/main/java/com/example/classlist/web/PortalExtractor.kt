package com.example.classlist.web

object PortalExtractor {
    internal const val PERSONAL_SCHEDULE_PATH = "/Gstudent/Course/StuCourseQuery.aspx"

    val script: String = """
        (function () {
          try {
            function clean(value) {
              return (value || '').replace(/\r/g, '').split('\n')
                .map(function (line) { return line.replace(/\s+/g, ' ').trim(); })
                .filter(Boolean).join('\n');
            }
            function documents() {
              var result = [], seen = [];
              function visit(doc) {
                if (!doc || seen.indexOf(doc) >= 0) return;
                seen.push(doc); result.push(doc);
                var frames = doc.querySelectorAll('iframe, frame');
                for (var i = 0; i < frames.length; i++) {
                  try { visit(frames[i].contentDocument); } catch (ignored) {}
                }
              }
              visit(document);
              return result;
            }
            function hasExplicitScheduleContext(doc) {
              var context = clean(doc.title);
              var nodes = doc.querySelectorAll('h1,h2,h3,h4,caption,.title,.page-title,.panel-title,.current,.selected');
              for (var i = 0; i < nodes.length; i++) context += '\n' + clean(nodes[i].innerText);
              var path = '';
              try { path = doc.location.pathname; } catch (ignored) {}
              return path.toLowerCase() === '${PERSONAL_SCHEDULE_PATH.lowercase()}' ||
                /个人课表|我的课表|学生课表|个人课程表|本学期课表|学期课表(?:信息)?查询|课表查询/.test(context);
            }
            function hasScheduleStructure(table) {
              var text = clean(table.innerText);
              var hasSections = /节次|上课时间|第\s*\d+\s*(?:[-~至]\s*\d+)?\s*节/.test(text);
              if (!hasSections) return false;
              var cells = table.querySelectorAll('td,th');
              for (var i = 0; i < cells.length; i++) {
                var value = clean(cells[i].innerText);
                if (/[A-Za-z\u3400-\u9FFF]/.test(value) &&
                    !/^(?:星期|周|礼拜)?[一二三四五六日天]$/.test(value) &&
                    !/^(?:节次|上课时间|课程|课程名称|上午|下午|晚上)$/.test(value)) return true;
              }
              return false;
            }
            function scoreTable(table) {
              var text = clean(table.innerText);
              var days = text.match(/星期[一二三四五六日]|周[一二三四五六日]/g) || [];
              var shortDays = Array.prototype.filter.call(table.querySelectorAll('td,th'), function (cell) {
                return /^[一二三四五六日]$/.test(clean(cell.innerText));
              });
              var dayCount = Math.max(days.length, shortDays.length);
              if (dayCount < 2 || !hasScheduleStructure(table)) return 0;
              var score = dayCount * 20;
              if (/节次|上课时间|课程名称/.test(text)) score += 12;
              score += Math.min(table.querySelectorAll('td,th').length, 60);
              return score;
            }
            function extract(table) {
              var cells = [], occupied = [], rows = table.rows;
              for (var row = 0; row < rows.length; row++) {
                occupied[row] = occupied[row] || [];
                var column = 0;
                for (var index = 0; index < rows[row].cells.length; index++) {
                  while (occupied[row][column]) column++;
                  var cell = rows[row].cells[index];
                  var rowSpan = Math.max(parseInt(cell.rowSpan || 1, 10), 1);
                  var columnSpan = Math.max(parseInt(cell.colSpan || 1, 10), 1);
                  cells.push({row: row, column: column, rowSpan: rowSpan,
                    columnSpan: columnSpan, text: clean(cell.innerText)});
                  for (var r = row; r < row + rowSpan; r++) {
                    occupied[r] = occupied[r] || [];
                    for (var c = column; c < column + columnSpan; c++) occupied[r][c] = true;
                  }
                  column += columnSpan;
                }
              }
              return cells;
            }

            var docs = documents();
            var hasPassword = docs.some(function (doc) { return !!doc.querySelector('input[type=password]'); });
            if (hasPassword) return JSON.stringify({status: 'login', message: '请先完成校方登录'});

            var contextConfirmed = docs.some(hasExplicitScheduleContext);
            if (contextConfirmed || window.__classlistPersonalScheduleRequested) {
              var best = null, bestScore = 0;
              for (var d = 0; d < docs.length; d++) {
                var tables = docs[d].querySelectorAll('table');
                for (var t = 0; t < tables.length; t++) {
                  var score = scoreTable(tables[t]);
                  if (score > bestScore) { best = tables[t]; bestScore = score; }
                }
              }
              if (best) return JSON.stringify({
                status: 'ok', cells: extract(best), title: clean(best.ownerDocument.title)
              });
            }

            var labels = ['学期课表信息查询', '个人课表', '我的课表', '学生课表', '个人课程表', '本学期课表', '课表查询', '课程表', '课表'];
            for (var labelIndex = 0; labelIndex < labels.length; labelIndex++) {
              for (var i = 0; i < docs.length; i++) {
                var controls = docs[i].querySelectorAll('a,button,input[type=button],input[type=submit]');
                for (var j = 0; j < controls.length; j++) {
                  var label = clean(controls[j].innerText || controls[j].value);
                  var path = '';
                  try { path = new URL(controls[j].href, docs[i].location.href).pathname; } catch (ignored) {}
                  if (label === labels[labelIndex] || path.toLowerCase() === '${PERSONAL_SCHEDULE_PATH.lowercase()}') {
                    window.__classlistPersonalScheduleRequested = true;
                    controls[j].click();
                    return JSON.stringify({status: 'navigating', message: '正在打开个人课表'});
                  }
                }
              }
            }
            return JSON.stringify({status: 'not_found', message: '未找到个人课表入口，请先在系统中打开个人课表再导入'});
          } catch (error) {
            return JSON.stringify({status: 'error', message: '页面读取失败'});
          }
        })();
    """.trimIndent()
}
