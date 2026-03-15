RST 渲染测试
=============

.. 这一段是注释，正常渲染时不应该显示。

这个文件用于验证 reStructuredText 渲染能力，包括注释、图片、表格、脚注、代码高亮和 list-table。

基本格式
--------

- 普通列表项
- **粗体** 和 *斜体*
- ``行内代码``

图片
----

.. image:: assets/test-image.svg
   :alt: 本地 SVG 测试图片
   :width: 240px

简单表格
--------

=====  ========  ==========
类型   是否支持  说明
=====  ========  ==========
标题   是        下划线标题
表格   是        simple table
脚注   是        [#second]_
=====  ========  ==========

网格表格
--------

+----------+----------+----------------+
| 项目     | 状态     | 备注           |
+==========+==========+================+
| image::  | 预期支持 | 本地图片显示   |
+----------+----------+----------------+
| code     | 预期支持 | 代码块高亮     |
+----------+----------+----------------+

list-table
----------

.. list-table:: 功能清单
   :header-rows: 1

   * - 功能
     - 结果
     - 备注
   * - list-table
     - 应显示为表格
     - 支持三列
   * - footnote
     - 应能跳转
     - RST 脚注

代码高亮
--------

.. code-block:: java

   public class Hello {
       public static void main(String[] args) {
           System.out.println("hello");
       }
   }

.. code-block:: python

   values = [1, 2, 3]
   total = sum(values)
   print(total)

.. code-block:: json

   {
     "renderer": "rst",
     "status": "testing"
   }

数学公式
--------

行内公式示例 :math:`e^{i\pi} + 1 = 0`。

.. math::

   a^2 + b^2 = c^2

脚注
----

这里引用一个脚注 [#second]_ 。

.. [#second] 这是 RST 脚注内容，用来验证当前新增的脚注解析逻辑。

链接
----

- 打开 `Markdown 首页 <index.md>`_
- 打开 `Markdown 子页面 <details.md>`_
- 打开 `RST 子页面 <details.rst>`_