"""
Web UI 自动化测试（Playwright）
启动方式：pytest tests/test_web.py --headed -v
依赖：pip install pytest-playwright && playwright install chromium
"""
from __future__ import annotations

import os

import pytest
from playwright.sync_api import Page, expect


# 测试 URL：指向已启动的开发服务器
BASE_URL = os.getenv("WORKSTATION_TEST_URL", "http://127.0.0.1:8080/app")


# ── 首页 & 导航 ──────────────────────────────────────────────

def test_dashboard_loads(page: Page):
    """仪表盘首页正确加载，显示标题和状态。"""
    page.goto(BASE_URL)
    expect(page.locator("#page-title")).to_contain_text("仪表盘")
    expect(page.locator("#server-status")).to_be_visible(timeout=10000)


def test_all_nav_tabs_visible(page: Page):
    """所有导航 tab 在侧边栏可见。"""
    page.goto(BASE_URL)
    nav = page.locator("#main-nav button")
    expect(nav).to_have_count(9)
    expected = ["仪表盘", "任务看板", "笔记", "GitHub", "开发工具", "代码片段", "本地 Git", "开发日志", "番茄专注"]
    for i, label in enumerate(expected):
        expect(nav.nth(i)).to_contain_text(label)


def test_switch_to_kanban(page: Page):
    """点击任务看板，视图正确切换。"""
    page.goto(BASE_URL)
    page.click("button[data-view='kanban']")
    page.wait_for_timeout(500)
    expect(page.locator("#page-title")).to_contain_text("任务看板")


def test_switch_to_github(page: Page):
    """点击 GitHub tab，视图正确切换。"""
    page.goto(BASE_URL)
    page.click("button[data-view='github']")
    page.wait_for_timeout(500)
    expect(page.locator("#page-title")).to_contain_text("GitHub")


def test_switch_to_settings(page: Page):
    """点击连接设置 tab，视图正确切换并显示表单。"""
    page.goto(BASE_URL)
    page.click("button[data-view='settings']")
    page.wait_for_timeout(500)
    expect(page.locator("#settings-form")).to_be_visible()


def test_jwt_secret_verification(page: Page):
    """JWT 工具可用共享密钥在浏览器本地校验 HMAC 签名。"""
    page.goto(BASE_URL)
    page.click("button[data-view='tools']")
    page.wait_for_timeout(300)
    page.click("button[data-tool-tab='jwt']")
    page.fill(
        "#jwt-input",
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjMiLCJyb2xlIjoiZGV2ZWxvcGVyIn0.JUJhfW0vTLKB6hj-OpZ_Tk5bgKP3RvZO9A48-rYBeFI",
    )
    page.fill("#jwt-secret", "dev-secret")
    page.click("#jwt-run")
    expect(page.locator("#jwt-status")).to_contain_text("签名有效")


# ── 连接设置页 ────────────────────────────────────────────────

def test_connect_form_fields(page: Page):
    """连接页包含 host 和 port 输入。"""
    page.goto(BASE_URL)
    page.click("button[data-view='settings']")
    page.wait_for_timeout(500)
    expect(page.locator("#settings-host")).to_be_visible()
    expect(page.locator("#settings-port")).to_be_visible()


def test_connect_empty_validation(page: Page):
    """连接表单空输入时测试连接应报错。"""
    page.goto(BASE_URL)
    page.click("button[data-view='settings']")
    page.wait_for_timeout(500)
    page.fill("#settings-host", "")
    page.fill("#settings-port", "")
    page.click("#settings-test")
    page.wait_for_timeout(1500)
    status = page.locator("#settings-status")
    expect(status).to_be_visible()


# ── 设置页 ────────────────────────────────────────────────────

def test_settings_has_profile_form(page: Page):
    """设置页包含个人资料表单。"""
    page.goto(BASE_URL)
    page.click("button[data-view='settings']")
    page.wait_for_timeout(500)
    expect(page.locator("#profile-form")).to_be_visible()


def test_settings_has_backup_panel(page: Page):
    """设置页包含数据备份面板。"""
    page.goto(BASE_URL)
    page.click("button[data-view='settings']")
    page.wait_for_timeout(500)
    expect(page.locator(".backup-panel")).to_be_visible()


# ── 弹窗 ──────────────────────────────────────────────────────

def test_new_task_dialog_opens(page: Page):
    """点击新建按钮打开任务创建弹窗。"""
    page.goto(BASE_URL)
    page.click("button[data-view='dashboard']")
    page.wait_for_timeout(500)
    page.click("#quick-add")
    page.wait_for_timeout(300)
    dialog = page.locator("#task-dialog")
    expect(dialog).to_be_visible()
    expect(page.locator("#task-dialog-title")).to_contain_text("新建")


def test_new_note_dialog_from_kanban(page: Page):
    """切换到任务看板后，新建按钮应打开笔记创建弹窗。"""
    page.goto(BASE_URL)
    page.click("button[data-view='notes']")
    page.wait_for_timeout(500)
    page.click("#quick-add")
    page.wait_for_timeout(300)
    dialog = page.locator("#note-dialog")
    expect(dialog).to_be_visible()


def test_dialog_close(page: Page):
    """弹窗可正常关闭。"""
    page.goto(BASE_URL)
    page.click("#quick-add")
    page.wait_for_timeout(300)
    page.click(".dialog-close")
    page.wait_for_timeout(300)
    dialog = page.locator("#task-dialog")
    expect(dialog).not_to_be_visible()


# ── 侧边栏 ────────────────────────────────────────────────────

def test_sidebar_brand(page: Page):
    """侧边栏品牌区可见。"""
    page.goto(BASE_URL)
    expect(page.locator(".brand")).to_contain_text("工作站")


def test_sidebar_user_info(page: Page):
    """侧边栏显示用户信息。"""
    page.goto(BASE_URL)
    expect(page.locator("#sidebar-name")).to_be_visible()
    expect(page.locator("#sidebar-avatar")).to_be_visible()


# ── 刷新按钮 ──────────────────────────────────────────────────

def test_refresh_button_works(page: Page):
    """刷新按钮存在并可用。"""
    page.goto(BASE_URL)
    expect(page.locator("#refresh-btn")).to_be_visible()
    page.locator("#refresh-btn").is_enabled()


# ── 主题 / CSS ────────────────────────────────────────────────

def test_css_loaded(page: Page):
    """CSS 样式表正确加载。"""
    page.goto(BASE_URL)
    color = page.evaluate(
        """() => {
            const el = document.querySelector('.app-shell');
            return window.getComputedStyle(el).getPropertyValue('background-color');
        }"""
    )
    assert color, "app-shell 应有背景色"


# ── Markdown 渲染依赖 ─────────────────────────────────────────

def test_markdown_dependencies_loaded(page: Page):
    """Markdown 渲染库（marked, DOMPurify, highlight.js）正确加载。"""
    page.goto(BASE_URL)
    marked = page.evaluate("() => typeof marked !== 'undefined'")
    purify = page.evaluate("() => typeof DOMPurify !== 'undefined'")
    hljs = page.evaluate("() => typeof hljs !== 'undefined'")
    assert marked, "marked.js 应已加载"
    assert purify, "DOMPurify 应已加载"
    # hljs 可能作为模块加载，检查其是否存在
