/* mint 主题的前端增强（渐进增强，没有脚本时页面仍然可读可点）：
   1) 移动端导航开关；
   2) 搜索页：读 /search/index.json，在浏览器里做子串匹配（§7.5 的静态搜索索引）。
   两件事都写成"找不到元素就静默退出"，任何一个选择器缺失都不会抛异常。 */
(function () {
  'use strict';

  /* ── 移动端导航 ─────────────────────────────────────────────────────── */
  function initNav() {
    var toggle = document.querySelector('.nav-toggle');
    var nav = document.getElementById('main-nav') || document.querySelector('.main-nav');
    if (!toggle || !nav) return;

    function setOpen(open) {
      nav.classList.toggle('is-open', open);
      toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
    }

    toggle.addEventListener('click', function () {
      setOpen(!nav.classList.contains('is-open'));
    });

    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape') setOpen(false);
    });

    document.addEventListener('click', function (event) {
      if (!nav.contains(event.target) && !toggle.contains(event.target)) setOpen(false);
    });

    // 窄屏下二级菜单点击展开（宽屏靠 :hover / :focus-within，见 site.css）
    var parents = nav.querySelectorAll('.main-nav__item');
    Array.prototype.forEach.call(parents, function (item) {
      var link = item.querySelector('.main-nav__link');
      if (!link || !item.querySelector('.main-nav__sub')) return;
      link.addEventListener('click', function (event) {
        if (window.matchMedia('(max-width: 760px)').matches) {
          event.preventDefault();
          item.classList.toggle('is-open');
        }
      });
    });
  }

  /* ── 搜索：静态索引 + 本地匹配 ─────────────────────────────────────── */
  function queryOf(name) {
    var match = new RegExp('[?&]' + name + '=([^&#]*)').exec(window.location.search);
    return match ? decodeURIComponent(match[1].replace(/\+/g, ' ')) : '';
  }

  function json(url) {
    return fetch(url, { credentials: 'same-origin' }).then(function (response) {
      if (!response.ok) throw new Error(url + ' → HTTP ' + response.status);
      return response.json();
    });
  }

  function pick(entry, keys) {
    for (var i = 0; i < keys.length; i++) {
      var value = entry[keys[i]];
      if (value !== undefined && value !== null && value !== '') return String(value);
    }
    return '';
  }

  function itemNode(entry, keyword) {
    var a = document.createElement('a');
    a.className = 'search-result__item';
    a.href = pick(entry, ['u', 'url']);

    var title = document.createElement('strong');
    title.className = 'search-result__title';
    title.textContent = pick(entry, ['t', 'title']) || keyword;
    a.appendChild(title);

    var body = pick(entry, ['s', 'summary']);
    if (!body) {
      var haystack = pick(entry, ['b', 'body']);
      var at = haystack.indexOf(keyword);
      body = at < 0 ? haystack.slice(0, 80) : haystack.slice(Math.max(0, at - 30), at + 60);
    }
    if (body) {
      var p = document.createElement('span');
      p.className = 'search-result__summary';
      p.textContent = body;
      a.appendChild(p);
    }
    var meta = document.createElement('span');
    meta.className = 'search-result__meta';
    meta.textContent = [pick(entry, ['c', 'category']), pick(entry, ['p', 'date'])]
      .filter(Boolean).join(' · ');
    a.appendChild(meta);
    return a;
  }

  function renderResults(box, keyword, entries) {
    box.textContent = '';
    var hit = entries.filter(function (entry) {
      var haystack = [
        pick(entry, ['t', 'title']),
        pick(entry, ['s', 'summary']),
        pick(entry, ['b', 'body']),
        pick(entry, ['c', 'category'])
      ].join(' ').toLowerCase();
      return haystack.indexOf(keyword.toLowerCase()) >= 0;
    });

    var info = document.createElement('p');
    info.className = 'search-result__count';
    info.textContent = hit.length
      ? '匹配到 ' + hit.length + ' 条（最多列出 30 条）'
      : '没有匹配「' + keyword + '」的内容，换个词试试，或到标签总览里翻。';
    box.appendChild(info);

    hit.slice(0, 30).forEach(function (entry) {
      box.appendChild(itemNode(entry, keyword));
    });
  }

  function fail(box, reason) {
    box.textContent = '';
    var p = document.createElement('p');
    p.className = 'state-empty';
    p.textContent = '本地搜索暂时不可用（' + reason + '）。可以到 /tags/ 标签总览或 /archive/ 归档里翻。';
    box.appendChild(p);
  }

  function initSearch() {
    var box = document.querySelector('[data-cms-search-result]');
    if (!box) return;
    var keyword = queryOf('q').trim();
    if (!keyword) return;

    json('/search/index.json')
      .then(function (manifest) {
        var shards = (manifest && manifest.shards) || [];
        if (!shards.length) throw new Error('索引里没有分片');
        return Promise.all(shards.map(function (name) {
          return json('/search/' + name);
        }));
      })
      .then(function (shards) {
        var entries = [];
        shards.forEach(function (shard) {
          if (Array.isArray(shard)) entries = entries.concat(shard);
        });
        renderResults(box, keyword, entries);
      })
      .catch(function (error) {
        fail(box, error && error.message ? error.message : '未知错误');
      });
  }

  function boot() {
    initNav();
    initSearch();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();
