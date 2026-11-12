import { expect, test } from '@playwright/test';
import { readFile, writeFile } from 'node:fs/promises';
import { join } from 'node:path';

const pagePath = join(process.cwd(), '.cache/browser-site/docs/README.md');
test('edits and saves through the actual page, middleware and HMR', async ({ page }) => {
  await page.goto('./');
  await page.getByRole('button', { name: '编辑页面', exact: true }).click();
  const paragraph = page.locator('.dev-edit-surface > p').first();
  await paragraph.fill('Changed by browser');
  await page.getByRole('button', { name: '提交', exact: true }).click();
  await expect(page.getByRole('button', { name: '编辑页面', exact: true })).toBeVisible();
  await expect(page.locator('#content')).toContainText('Changed by browser');
  expect(await readFile(pagePath, 'utf8')).toContain('Changed by browser');
});
test('cancel preserves the source and requires confirmation for changed content', async ({ page }) => {
  const source = await readFile(pagePath, 'utf8');
  await page.goto('./');
  await page.getByRole('button', { name: '编辑页面', exact: true }).click();
  await page.locator('.dev-edit-surface > p').first().fill('Discard this text');
  await page.getByRole('button', { name: '取消', exact: true }).click();
  await page.getByRole('button', { name: '继续编辑', exact: true }).click();
  await expect(page.locator('.dev-edit-surface')).toContainText('Discard this text');
  await page.getByRole('button', { name: '取消', exact: true }).click();
  await page.getByRole('button', { name: '确定放弃', exact: true }).click();
  await expect(page.getByRole('button', { name: '编辑页面', exact: true })).toBeVisible();
  expect(await readFile(pagePath, 'utf8')).toBe(source);
});
test('code highlighting and table actions retain editor selection', async ({ page }) => {
  await page.goto('./');
  await page.getByRole('button', { name: '编辑页面', exact: true }).click();
  const gutter = page.locator('.dev-edit-code-line-number');
  await gutter.first().click();
  await expect(page.getByRole('textbox', { name: '代码高亮行' })).toHaveValue('1,2');
  await page.locator('.dev-edit-surface td').first().click();
  const before = await page.locator('.dev-edit-surface tr').count();
  await page.getByRole('button', { name: '在下方插入行', exact: true }).click();
  await expect(page.locator('.dev-edit-surface tr')).toHaveCount(before + 1);
  await page.getByRole('button', { name: '取消', exact: true }).click();
  await page.getByRole('button', { name: '确定放弃', exact: true }).click();
});

test('inline code shortcut and clear formatting preserve selected text', async ({ page }) => {
  await page.goto('./');
  await page.getByRole('button', { name: '编辑页面', exact: true }).click();
  const paragraph = page.locator('.dev-edit-surface > p').first();
  await paragraph.fill('Inline formatting');
  const surface = page.locator('.dev-edit-surface');
  await surface.press('Shift+Home');
  await surface.press('ControlOrMeta+e');
  await expect(paragraph.locator('code')).toHaveText('Inline formatting');
  await page.getByRole('button', { name: '粗体', exact: true }).click();
  await expect(paragraph.locator('strong code')).toHaveText('Inline formatting');
  await page.getByRole('button', { name: '清除格式', exact: true }).click();
  await expect(paragraph).toHaveText('Inline formatting');
  await expect(paragraph.locator('code, strong')).toHaveCount(0);
  await paragraph.fill('');
  await surface.evaluate((element) => {
    const clipboardData = new DataTransfer();
    clipboardData.setData('text/plain', String.fromCharCode(96) + 'Pasted code' + String.fromCharCode(96));
    element.dispatchEvent(new ClipboardEvent('paste', { clipboardData, bubbles: true, cancelable: true }));
  });
  await expect(paragraph.locator('code')).toHaveText('Pasted code');
  await page.getByRole('button', { name: '取消', exact: true }).click();
  await page.getByRole('button', { name: '确定放弃', exact: true }).click();
});

for (const viewport of [
  { width: 1440, height: 900 },
  { width: 390, height: 844 },
]) {
  for (const colorScheme of ['light', 'dark'] as const) {
    test('table alignment survives recovery at ' + viewport.width + 'px in ' + colorScheme + ' mode', async ({ page }) => {
      await page.setViewportSize(viewport);
      await page.emulateMedia({ colorScheme });
      await page.goto('./');
      await page.getByRole('button', { name: '编辑页面', exact: true }).click();
      await page.locator('.dev-edit-surface th').first().click();
      await page.getByRole('button', { name: '单元格右对齐', exact: true }).click();
      await expect(page.locator('.dev-edit-surface th').first()).toHaveCSS('text-align', 'right');
      const alignmentButton = await page.getByRole('button', { name: '单元格右对齐', exact: true }).boundingBox();
      expect(alignmentButton!.x).toBeGreaterThanOrEqual(0);
      expect(alignmentButton!.x + alignmentButton!.width).toBeLessThanOrEqual(viewport.width);
      await page.getByRole('button', { name: '提交', exact: true }).click();
      await expect(page.getByRole('button', { name: '编辑页面', exact: true })).toBeVisible();
      await expect(page.locator('#content th').first()).toHaveCSS('text-align', 'right');
      await page.reload();
      await page.getByRole('button', { name: '编辑页面', exact: true }).click();
      await expect(page.locator('.dev-edit-surface th').first()).toHaveCSS('text-align', 'right');
      await page.locator('.dev-edit-surface th').first().click();
      await page.getByRole('button', { name: '单元格居中', exact: true }).click();
      await expect(page.locator('.dev-edit-surface th').first()).toHaveCSS('text-align', 'center');
      await page.getByRole('button', { name: '单元格左对齐', exact: true }).click();
      await expect(page.locator('.dev-edit-surface th').first()).toHaveCSS('text-align', 'left');
      await page.getByRole('button', { name: '取消', exact: true }).click();
      await page.getByRole('button', { name: '确定放弃', exact: true }).click();
    });
  }
}

test('external source changes produce a conflict and retain the browser draft', async ({ page }) => {
  const original = await readFile(pagePath, 'utf8');
  await page.goto('./');
  await page.getByRole('button', { name: '编辑页面', exact: true }).click();
  await page.locator('.dev-edit-surface > p').first().fill('Browser draft');
  await writeFile(pagePath, original + '\nExternal edit.\n', 'utf8');
  await page.getByRole('button', { name: '提交', exact: true }).click();
  await expect(page.locator('.dev-edit-status')).toContainText('版本冲突');
  await expect(page.locator('.dev-edit-surface')).toContainText('Browser draft');
  expect(await readFile(pagePath, 'utf8')).toContain('External edit.');
  await page.getByRole('button', { name: '取消', exact: true }).click();
  await page.getByRole('button', { name: '确定放弃', exact: true }).click();
});
test('navigation works after leaving the editor', async ({ page }) => {
  await page.goto('./');
  await page.getByRole('button', { name: '编辑页面', exact: true }).click();
  await page.getByRole('button', { name: '取消', exact: true }).click();
  await page.goto('./other.html');
  await expect(page.locator('#content')).toContainText('Other page');
  await expect(page.getByRole('button', { name: '编辑页面', exact: true })).toBeVisible();
});

test('quote wrapping, list switching and item splitting use one editor model', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  page.on('console', (message) => {
    if (message.text().includes('prosemirror-model is loaded more than once')) errors.push(message.text());
  });
  await page.goto('./');
  await page.getByRole('button', { name: '编辑页面', exact: true }).click();
  const paragraph = page.locator('.dev-edit-surface > p').first();
  const text = 'Quote and list text';
  await paragraph.fill(text);
  await page.getByRole('button', { name: '引用', exact: true }).click();
  await expect(page.locator('.dev-edit-surface > blockquote > p').first()).toHaveText(text);
  await page.getByRole('button', { name: '引用', exact: true }).click();
  await page.getByRole('button', { name: '无序列表', exact: true }).click();
  await expect(page.locator('.dev-edit-surface > ul > li > p').first()).toHaveText(text);
  await page.getByRole('button', { name: '有序列表', exact: true }).click();
  const items = page.locator('.dev-edit-surface > ol > li');
  await expect(items).toHaveCount(1);
  await page.locator('.dev-edit-surface').press('End');
  await page.keyboard.press('Enter');
  await page.keyboard.insertText('Next item');
  await expect(items).toHaveCount(2);
  await expect(items.last()).toHaveText('Next item');
  expect(errors).toEqual([]);

  await page.getByRole('button', { name: '取消', exact: true }).click();
  await page.getByRole('button', { name: '确定放弃', exact: true }).click();
});
