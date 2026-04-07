For a simple Thymeleaf + Bootstrap stack, the most efficient approach is shared JS files with Thymeleaf fragments — no build tools needed.

The Pattern
1. Organize your static JS files
   src/main/resources/static/js/
   common.js        ← shared utilities, helpers
   components.js    ← reusable UI behaviors
   pages/
   dashboard.js   ← page-specific code
   profile.js
2. Create a Thymeleaf layout fragment (fragments/layout.html)
   html<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head th:fragment="head(title)">
  <title th:text="${title}">App</title>
  <link rel="stylesheet" th:href="@{/css/bootstrap.min.css}"/>
</head>

<body>
  <!-- your page content -->

  <div th:fragment="scripts">
    <!-- Always loaded -->
    <script th:src="@{/js/bootstrap.bundle.min.js}"></script>
    <script th:src="@{/js/common.js}"></script>
  </div>
</body>
</html>
3. Use it in pages, with optional page-specific scripts
html<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{fragments/layout :: head('Dashboard')}"></head>
<body>

  <main>
    <!-- page content -->
  </main>

  <!-- Common scripts -->
  <div th:replace="~{fragments/layout :: scripts}"></div>

  <!-- Page-specific script, loaded only here -->
  <script th:src="@{/js/pages/dashboard.js}"></script>

</body>
</html>
Key Principles for common.js
Structure it as a simple namespace to avoid global variable collisions:
javascript// common.js
const App = {
  // Shared config (can be set by Thymeleaf inline)
  config: {},

utils: {
formatDate(date) { /* ... */ },
debounce(fn, ms) { /* ... */ }
},

http: {
async post(url, data) {
const csrf = document.querySelector('meta[name="_csrf"]');
return fetch(url, {
method: 'POST',
headers: {
'Content-Type': 'application/json',
...(csrf ? { [csrf.getAttribute('content')]: csrf.getAttribute('content') } : {})
},
body: JSON.stringify(data)
});
}
},

ui: {
showToast(message, type = 'success') { /* Bootstrap toast logic */ }
}
};
Passing Server Data to JS (without API calls)
Use Thymeleaf inline scripts sparingly, only for bootstrapping data:
html<script th:inline="javascript">
App.config = {
userId: /*[[${user.id}]]*/ null,
contextPath: /*[[@{/}]]*/ '/',
csrfToken: /*[[${_csrf.token}]]*/ null
};
</script>
<!-- Then load your scripts AFTER -->
<script th:src="@{/js/common.js}"></script>
Why this works well for your stack

Zero build tooling — plain .js files served statically
Thymeleaf fragments handle the "layout" problem cleanly
Browser caches common files across pages
No module bundler needed since Bootstrap already covers most UI needs
Easy to grow into — if you later want to add a bundler, the file structure is already sane

The only thing to watch is script load order: always put common.js before page-specific scripts, and put inline data bootstrapping before both.