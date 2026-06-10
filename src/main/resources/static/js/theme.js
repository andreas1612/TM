function applyTheme(theme) {
  document.documentElement.setAttribute("data-theme", theme);
  localStorage.setItem("theme", theme);

  const button = document.getElementById("themeToggle");
  if (button) {
    button.innerText = theme === "dark" ? "Light Mode" : "Dark Mode";
    button.setAttribute(
      "aria-label",
      theme === "dark" ? "Switch to light mode" : "Switch to dark mode"
    );
    button.setAttribute(
      "title",
      theme === "dark" ? "Switch to light mode" : "Switch to dark mode"
    );
  }
}

function toggleTheme() {
  const current = document.documentElement.getAttribute("data-theme") || "light";
  const next = current === "dark" ? "light" : "dark";
  applyTheme(next);
}

const saved = localStorage.getItem("theme") || "light";
applyTheme(saved);

document.addEventListener("DOMContentLoaded", () => {
  const btn = document.getElementById("themeToggle");
  if (btn) btn.addEventListener("click", toggleTheme);
});
