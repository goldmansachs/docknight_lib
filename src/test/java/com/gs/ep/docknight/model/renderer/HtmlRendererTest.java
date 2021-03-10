/*
 *   Copyright 2020 Goldman Sachs.
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 */

package com.gs.ep.docknight.model.renderer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Maps;
import org.eclipse.collections.impl.factory.Sets;
import com.gs.ep.docknight.model.Attribute;
import com.gs.ep.docknight.model.ComparableBufferedImage;
import com.gs.ep.docknight.model.Element;
import com.gs.ep.docknight.model.Form;
import com.gs.ep.docknight.model.Form.FormType;
import com.gs.ep.docknight.model.Length;
import com.gs.ep.docknight.model.Length.Unit;
import com.gs.ep.docknight.model.PositionalElementList;
import com.gs.ep.docknight.model.attribute.BorderColor;
import com.gs.ep.docknight.model.attribute.BorderStyle;
import com.gs.ep.docknight.model.attribute.Caption;
import com.gs.ep.docknight.model.attribute.Color;
import com.gs.ep.docknight.model.attribute.Content;
import com.gs.ep.docknight.model.attribute.FontSize;
import com.gs.ep.docknight.model.attribute.FormData;
import com.gs.ep.docknight.model.attribute.Height;
import com.gs.ep.docknight.model.attribute.ImageData;
import com.gs.ep.docknight.model.attribute.InlineContent;
import com.gs.ep.docknight.model.attribute.Left;
import com.gs.ep.docknight.model.attribute.LetterSpacing;
import com.gs.ep.docknight.model.attribute.PageFooter;
import com.gs.ep.docknight.model.attribute.PageHeader;
import com.gs.ep.docknight.model.attribute.PageMargin;
import com.gs.ep.docknight.model.attribute.PageSize;
import com.gs.ep.docknight.model.attribute.PageStructure;
import com.gs.ep.docknight.model.attribute.PositionalContent;
import com.gs.ep.docknight.model.attribute.Stretch;
import com.gs.ep.docknight.model.attribute.Text;
import com.gs.ep.docknight.model.attribute.TextAlign;
import com.gs.ep.docknight.model.attribute.TextStyles;
import com.gs.ep.docknight.model.attribute.Top;
import com.gs.ep.docknight.model.attribute.Url;
import com.gs.ep.docknight.model.attribute.Width;
import com.gs.ep.docknight.model.element.Document;
import com.gs.ep.docknight.model.element.FormElement;
import com.gs.ep.docknight.model.element.HorizontalLine;
import com.gs.ep.docknight.model.element.Image;
import com.gs.ep.docknight.model.element.InlineBlock;
import com.gs.ep.docknight.model.element.Page;
import com.gs.ep.docknight.model.element.PageBreak;
import com.gs.ep.docknight.model.element.TextElement;
import com.gs.ep.docknight.model.element.VerticalLine;
import com.gs.ep.docknight.model.renderer.construct.HtmlConstruct;
import com.gs.ep.docknight.model.testutil.BoundingBox;
import com.gs.ep.docknight.model.testutil.GroupedBoundingBox;
import com.gs.ep.docknight.model.testutil.PositionalDocDrawer;
import com.gs.ep.docknight.model.transformer.PositionalTextGroupingTransformer;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.jsoup.parser.Tag;
import org.junit.Ignore;
import org.junit.Test;

public class HtmlRendererTest {

  public static Document getDocumentForMargins() {
    return new Document()
        .add(new Content(
            new Page()
                .add(new Content(
                    new InlineBlock()
                        .add(new InlineContent(
                            new TextElement()
                                .add(new Text(
                                    "1. DocKnight provides functionality for Digitizing Documents."))

                        ))
                )),
            new Page()
                .add(new Content(
                    new InlineBlock()
                        .add(new InlineContent(
                            new TextElement()
                                .add(new Text(
                                    "2. DocKnight also provides a lot of functionality for Documents' Generation."))

                        ))
                ))
        ));
  }

  private static String getRenderedHtml(Document document) {
    return new HtmlRenderer().withSortedHtmlAttributesWhileRendering(true).render(document);
  }

  private static org.jsoup.nodes.Element getRenderedHtmlNode(Element element) {
    org.jsoup.nodes.Element htmlNode = new org.jsoup.nodes.Element(Tag.valueOf("div"), "");
    new HtmlRenderer().handleElement(element, htmlNode);
    HtmlRenderer.sortHtmlNodeAttributes(htmlNode);
    return htmlNode;
  }

  private static org.jsoup.nodes.Element getRenderedHtmlNode(Attribute attribute) {
    org.jsoup.nodes.Element htmlNode = new org.jsoup.nodes.Element(Tag.valueOf("div"), "");
    new HtmlRenderer().handleAttribute(attribute, htmlNode);
    HtmlRenderer.sortHtmlNodeAttributes(htmlNode);
    return htmlNode;
  }

  @Test
  public void testHandleParagraphElement() {
    InlineBlock paragraph = new InlineBlock()
        .add(new TextAlign("left"))
        .add(new BorderStyle(BorderStyle.SOLID))
        .add(new BorderColor(java.awt.Color.BLACK))
        .add(new InlineContent(
            new InlineBlock()
                .add(new Color(java.awt.Color.BLUE))
                .add(new InlineContent(
                    new InlineBlock()
                        .add(new FontSize(new Length(22, Unit.pt)))
                        .add(new InlineContent(
                            new TextElement()
                                .add(new Text("There text has some change"))
                        ))

                ))
        ));

    org.jsoup.nodes.Element htmlNode = getRenderedHtmlNode(paragraph);
    assertEquals(HtmlConstruct.SPAN_TAG, htmlNode.child(0).tagName());
  }

  @Test
  public void testHandleInlineBlockElement() {
    InlineBlock inlineBlock = new InlineBlock()
        .add(new InlineContent(
            new TextElement()
                .add(new Text("There text has some change"))
        ));

    org.jsoup.nodes.Element htmlNode = getRenderedHtmlNode(inlineBlock);
    assertEquals("span", htmlNode.child(0).tagName());
  }

  @Test
  public void testHandleContentAttributeSize() {
    Content content = new Content(
        new InlineBlock()
            .add(new InlineContent(
                new TextElement()
                    .add(new Text("There text has some change"))
            )),
        new TextElement()
            .add(new Text("First Sentence")),
        new TextElement()
            .add(new Text("Second Sentence"))
    );
    org.jsoup.nodes.Element htmlNode = getRenderedHtmlNode(content);
    assertEquals(3, htmlNode.childNodeSize());
  }

  @Test
  public void testHandleTextElement() {
    TextElement textElement = new TextElement()
        .add(new TextStyles(TextStyles.BOLD))
        .add(new Text("Goldman Sachs"));

    org.jsoup.nodes.Element htmlNode = getRenderedHtmlNode(textElement);
    assertEquals("span", htmlNode.child(0).tagName());
    assertTrue(htmlNode.child(0).toString().contains(HtmlConstruct.FONT_WEIGHT + ":" + "bold"));
  }

  @Test
  public void testHandlePageMarginAttribute() {
    Document documentForMargins = getDocumentForMargins();
    Document document1 = documentForMargins
        .add(new PageMargin(new Length(12, Unit.px)));

    String expectedHtml1 = "<html>\n"
        + " <head>\n"
        + "  <meta charset=\"UTF-8\">\n"
        + "  <style type=\"text/css\">@page { margin: 12px; }</style>\n"
        + " </head>\n"
        + " <body>\n"
        + "  <div>\n"
        + "   <div style=\"border-color:#d3d3d3;border-style:solid;box-sizing:border-box\">\n"
        + "    <span><span>1. DocKnight provides functionality for Digitizing Documents.</span></span>\n"
        + "   </div>\n"
        + "   <div style=\"border-color:#d3d3d3;border-style:solid;box-sizing:border-box\">\n"
        + "    <span><span>2. DocKnight also provides a lot of functionality for Documents' Generation.</span></span>\n"
        + "   </div>\n"
        + "  </div>\n"
        + " </body>\n"
        + "</html>";
    assertEquals(expectedHtml1, getRenderedHtml(document1));

    Document documentForMargins2 = getDocumentForMargins();
    Document document2 = documentForMargins2
        .add(new PageSize(PageSize.A4))
        .add(new PageMargin(new Length(12, Unit.px), new Length(24, Unit.px),
            new Length(36, Unit.px), new Length(48, Unit.px)));

    String expectedHtml2 = "<html>\n"
        + " <head>\n"
        + "  <meta charset=\"UTF-8\">\n"
        + "  <style type=\"text/css\">@page { size: 595pt 842pt; }</style>\n"
        + "  <style type=\"text/css\">@page { margin: 12px 24px 36px 48px; }</style>\n"
        + " </head>\n"
        + " <body>\n"
        + "  <div>\n"
        + "   <div style=\"border-color:#d3d3d3;border-style:solid;box-sizing:border-box\">\n"
        + "    <span><span>1. DocKnight provides functionality for Digitizing Documents.</span></span>\n"
        + "   </div>\n"
        + "   <div style=\"border-color:#d3d3d3;border-style:solid;box-sizing:border-box\">\n"
        + "    <span><span>2. DocKnight also provides a lot of functionality for Documents' Generation.</span></span>\n"
        + "   </div>\n"
        + "  </div>\n"
        + " </body>\n"
        + "</html>";
    assertEquals(expectedHtml2, getRenderedHtml(document2));
  }

  @Test
  public void testRender() {
    Document document = new Document()
        .add(new PageHeader(new InlineBlock()
            .add(new FontSize(new Length(16, Unit.pt)))
            .add(new InlineContent(
                new TextElement()
                    .add(new Text("This header is in meta."))
            ))
        ))
        .add(new PageFooter(new InlineBlock()
            .add(new FontSize(new Length(16, Unit.pt)))
            .add(new InlineContent(
                new TextElement()
                    .add(new Text("This footer is in meta too!"))
                    .add(new LetterSpacing(new Length(-0.052, Unit.em)))
            ))
        ))
        .add(new Content(
            new InlineBlock()
                .add(new InlineContent(
                    new TextElement()
                        .add(new Text(
                            "It is document\ttemplate  authoring platform.\nWe also use it for document text extraction. 1 < 2"))
                ))
                .add(new Color(java.awt.Color.RED))
        ));

    String expectedHtml = "<html>\n"
        + " <head>\n"
        + "  <meta charset=\"UTF-8\">\n"
        + " </head>\n"
        + " <body>\n"
        + "  <div>\n"
        + "   <span style=\"color:#FF0000\"><span>It is document&nbsp; &nbsp; template &nbsp;authoring platform.<br>We also use it for document text extraction. 1 &lt; 2</span></span>\n"
        + "  </div>\n"
        + "  <div class=\"page-header\" style=\"display:none\">\n"
        + "   <span style=\"font-size:16pt\"><span>This header is in meta.</span></span>\n"
        + "  </div>\n"
        + "  <div class=\"page-footer\" style=\"display:none\">\n"
        + "   <span style=\"font-size:16pt\"><span style=\"letter-spacing:-0.052em\">This footer is in meta too!</span></span>\n"
        + "  </div>\n"
        + " </body>\n"
        + "</html>";
    assertEquals(expectedHtml, getRenderedHtml(document));
  }

  @Test
  public void testRenderImage() throws Exception {
    Document document = new Document()
        .add(new Content(
            new Image()
                .add(new Url(
                    "https://temp.com/download/attachments/169908361/temp.PNG?version=2&amp;modificationDate=1466526990344&amp;api=v2"))
                .add(new Caption(new TextElement().add(new Text("Goldman Logo"))))
        ));

    String expectedHtml = "<html>\n"
        + " <head>\n"
        + "  <meta charset=\"UTF-8\">\n"
        + " </head>\n"
        + " <body>\n"
        + "  <div>\n"
        + "   <figure>\n"
        + "    <img src=\"https://temp.com/download/attachments/169908361/temp.PNG?version=2&amp;amp;modificationDate=1466526990344&amp;amp;api=v2\">\n"
        + "    <figcaption>\n"
        + "     <span>Goldman Logo</span>\n"
        + "    </figcaption>\n"
        + "   </figure>\n"
        + "  </div>\n"
        + " </body>\n"
        + "</html>";
    assertEquals(expectedHtml, getRenderedHtml(document));

    document = new Document()
        .add(new Content(
            new Image()
                .add(new Url(
                    "https://temp.com/download/attachments/169908361/temp.PNG?version=2&amp;modificationDate=1466526990344&amp;api=v2"))
        ));
    expectedHtml = "<html>\n"
        + " <head>\n"
        + "  <meta charset=\"UTF-8\">\n"
        + " </head>\n"
        + " <body>\n"
        + "  <div>\n"
        + "   <img src=\"https://temp.com/download/attachments/169908361/temp.PNG?version=2&amp;amp;modificationDate=1466526990344&amp;amp;api=v2\">\n"
        + "  </div>\n"
        + " </body>\n"
        + "</html>";
    assertEquals(expectedHtml, getRenderedHtml(document));

    //TODO: image in base64 is coming out differently on gitlab machine
//    String pngImageInBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR42mNgAAIAAAUAAen63NgAAAAASUVORK5CYII=";
//    document = new Document()
//        .add(new Content(
//            new Image()
//                .add(new ImageData(ComparableBufferedImage.parseBase64PngBinary(pngImageInBase64)))
//                .add(new Caption(new TextElement().add(new Text("Something"))))
//        ));
//    expectedHtml = "<html>\n"
//        + " <head>\n"
//        + "  <meta charset=\"UTF-8\">\n"
//        + " </head>\n"
//        + " <body>\n"
//        + "  <div>\n"
//        + "   <figure>\n"
//        + "    <img src=\"data:image/png;base64," + pngImageInBase64 + "\">\n"
//        + "    <figcaption>\n"
//        + "     <span>Something</span>\n"
//        + "    </figcaption>\n"
//        + "   </figure>\n"
//        + "  </div>\n"
//        + " </body>\n"
//        + "</html>";
//    assertEquals(expectedHtml, getRenderedHtml(document));
  }

  @Test
  public void testLines() throws Exception {
    Document document = new Document()
        .add(new Content(
            new Page()
                .add(new Width(new Length(500, Unit.pt)))
                .add(new Height(new Length(500, Unit.pt)))
                .add(new PositionalContent(new PositionalElementList<Element>(Lists.mutable.of(
                    new HorizontalLine()
                        .add(new Top(new Length(100, Unit.pt)))
                        .add(new Left(new Length(100, Unit.pt)))
                        .add(new Stretch(new Length(200, Unit.pt))),
                    new VerticalLine()
                        .add(new Top(new Length(110, Unit.pt)))
                        .add(new Left(new Length(110, Unit.pt)))
                        .add(new Stretch(new Length(200, Unit.pt)))
                ))))
        ));

    String expectedHtml = "<html>\n" +
        " <head>\n" +
        "  <meta charset=\"UTF-8\">\n" +
        " </head>\n" +
        " <body>\n" +
        "  <div id=\"Root\" style=\"position:absolute\">\n" +
        "   <div id=\"Content-0\" style=\"border-color:#d3d3d3;border-style:solid;box-sizing:border-box;height:500pt;width:500pt\">\n"
        +
        "    <div style=\"background-color:#d3d3d3;height:1px;left:100pt;position:absolute;top:100pt;width:200pt;z-index:-1\"></div>\n"
        +
        "    <div style=\"background-color:#d3d3d3;height:200pt;left:110pt;position:absolute;top:110pt;width:1px;z-index:-1\"></div>\n"
        +
        "   </div>\n" +
        "  </div>\n" +
        " </body>\n" +
        "</html>";
    assertEquals(expectedHtml, getRenderedHtml(document));
  }

  @Test
  public void testHandlePositionalContentAttribute() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    drawer.setFont(PDType1Font.TIMES_ROMAN, 14);

    double leftPageMargin = 100;
    double topPageMargin = 100;
    double lineSpacing = 10;

    BoundingBox header1 = drawer.drawTextAt(leftPageMargin, topPageMargin, "Header1");
    BoundingBox textElement11 = drawer
        .drawTextAt(header1.getLeftBottomPlus(0, 3 * lineSpacing), "TextElement11");
    BoundingBox textElement12 = drawer
        .drawTextAt(textElement11.getLeftBottomPlus(0, lineSpacing), "TextElement12");
    drawer.drawTextAt(textElement12.getLeftBottomPlus(0, lineSpacing), "TextElement13");
    // TODO - Uncomment the below lines once we get consistent image binary over gitlab
//    BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
//    Graphics2D graphics = image.createGraphics();
//    graphics.setColor(java.awt.Color.GREEN);
//    graphics.fillRect(0, 0, 10, 10);
//    drawer.drawImageAt(100, 500, image);

    drawer.addPage();
    BoundingBox header2 = drawer.drawTextAt(leftPageMargin, topPageMargin, "Header2");
    BoundingBox textElement21 = drawer
        .drawTextAt(header2.getLeftBottomPlus(0, 3 * lineSpacing), "TextElement21");
    BoundingBox textElement22 = drawer
        .drawTextAt(textElement21.getLeftBottomPlus(0, lineSpacing), "TextElement22");
    drawer.drawTextAt(textElement22.getLeftBottomPlus(0, lineSpacing), "TextElement23");

    drawer.addPage();
    drawer.setWriteAndFillColor(java.awt.Color.GREEN);
    drawer.drawRectangleAt(100, 400, 300, 50, RenderingMode.FILL);
    drawer.setWriteAndFillColor(java.awt.Color.WHITE);
    drawer.drawTextAt(110, 410, "Visible text because it is white on green");

    Document document = new PositionalTextGroupingTransformer().transform(drawer.getDocument());

    String expectedHtml = "<html>\n" +
        " <head>\n" +
        "  <meta charset=\"UTF-8\">\n" +
        " </head>\n" +
        " <body>\n" +
        "  <div id=\"Root\" style=\"position:absolute\">\n" +
        "   <div id=\"Content-0\" style=\"border-color:#d3d3d3;border-style:solid;box-sizing:border-box;height:792pt;width:612pt\">\n"
        +
        "    <span data-below-group-id=\"Content-0_PositionalContent-1\" data-vertical-group-id=\"Content-0_PositionalContent-0\" data-visual-height=\"23.77pt\" data-visual-left=\"100pt\" data-visual-top=\"100pt\" data-visual-width=\"47.42pt\" id=\"Content-0_PositionalContent-0\" style=\"font-family:Times;font-size:14pt;left:100pt;line-height:75%;margin-left:-5px;padding-left:5px;position:absolute;top:100pt;white-space:nowrap\">Header1</span>\n"
        +
        "    <span data-above-group-id=\"Content-0_PositionalContent-0\" data-vertical-group-id=\"Content-0_PositionalContent-1\" data-visual-height=\"63.3pt\" data-visual-left=\"100pt\" data-visual-top=\"139.77pt\" data-visual-width=\"86.32pt\" id=\"Content-0_PositionalContent-1\" style=\"font-family:Times;font-size:14pt;left:100pt;line-height:75%;margin-left:-5px;padding-left:5px;position:absolute;top:139.77pt;white-space:nowrap\">TextElement11</span>\n"
        +
        "    <span data-above-group-id=\"Content-0_PositionalContent-0\" data-vertical-group-id=\"Content-0_PositionalContent-1\" data-visual-height=\"63.3pt\" data-visual-left=\"100pt\" data-visual-top=\"139.77pt\" data-visual-width=\"86.32pt\" id=\"Content-0_PositionalContent-2\" style=\"font-family:Times;font-size:14pt;left:100pt;line-height:75%;margin-left:-5px;padding-left:5px;position:absolute;top:159.53pt;white-space:nowrap\">TextElement12</span>\n"
        +
        "    <span data-above-group-id=\"Content-0_PositionalContent-0\" data-vertical-group-id=\"Content-0_PositionalContent-1\" data-visual-height=\"63.3pt\" data-visual-left=\"100pt\" data-visual-top=\"139.77pt\" data-visual-width=\"86.32pt\" id=\"Content-0_PositionalContent-3\" style=\"font-family:Times;font-size:14pt;left:100pt;line-height:75%;margin-left:-5px;padding-left:5px;position:absolute;top:179.3pt;white-space:nowrap\">TextElement13</span>\n"
//        +
//        "    <img id=\"Content-0_PositionalContent-4\" src=\"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAoAAAAKCAIAAAACUFjqAAAAEUlEQVR42mNg+M+AD41KY0MAVC5jnQLZ8LkAAAAASUVORK5CYII=\" style=\"height:10pt;left:100pt;position:absolute;top:500pt;width:10pt;z-index:-1\">\n"
        +
        "   </div>\n" +
        "   <div id=\"Content-1\" style=\"border-color:#d3d3d3;border-style:solid;box-sizing:border-box;height:792pt;width:612pt\">\n"
        +
        "    <span data-below-group-id=\"Content-1_PositionalContent-1\" data-vertical-group-id=\"Content-1_PositionalContent-0\" data-visual-height=\"20.37pt\" data-visual-left=\"100pt\" data-visual-top=\"892pt\" data-visual-width=\"40.64pt\" id=\"Content-1_PositionalContent-0\" style=\"font-family:Times;font-size:12pt;left:100pt;line-height:75%;margin-left:-5px;padding-left:5px;position:absolute;top:892pt;white-space:nowrap\">Header2</span>\n"
        +
        "    <span data-above-group-id=\"Content-1_PositionalContent-0\" data-vertical-group-id=\"Content-1_PositionalContent-1\" data-visual-height=\"57.11pt\" data-visual-left=\"100pt\" data-visual-top=\"930.37pt\" data-visual-width=\"73.99pt\" id=\"Content-1_PositionalContent-1\" style=\"font-family:Times;font-size:12pt;left:100pt;line-height:75%;margin-left:-5px;padding-left:5px;position:absolute;top:930.37pt;white-space:nowrap\">TextElement21</span>\n"
        +
        "    <span data-above-group-id=\"Content-1_PositionalContent-0\" data-vertical-group-id=\"Content-1_PositionalContent-1\" data-visual-height=\"57.11pt\" data-visual-left=\"100pt\" data-visual-top=\"930.37pt\" data-visual-width=\"73.99pt\" id=\"Content-1_PositionalContent-2\" style=\"font-family:Times;font-size:12pt;left:100pt;line-height:75%;margin-left:-5px;padding-left:5px;position:absolute;top:948.74pt;white-space:nowrap\">TextElement22</span>\n"
        +
        "    <span data-above-group-id=\"Content-1_PositionalContent-0\" data-vertical-group-id=\"Content-1_PositionalContent-1\" data-visual-height=\"57.11pt\" data-visual-left=\"100pt\" data-visual-top=\"930.37pt\" data-visual-width=\"73.99pt\" id=\"Content-1_PositionalContent-3\" style=\"font-family:Times;font-size:12pt;left:100pt;line-height:75%;margin-left:-5px;padding-left:5px;position:absolute;top:967.11pt;white-space:nowrap\">TextElement23</span>\n"
        +
        "   </div>\n" +
        "   <div id=\"Content-2\" style=\"border-color:#d3d3d3;border-style:solid;box-sizing:border-box;height:792pt;width:612pt\">\n"
        +
        "    <span data-vertical-group-id=\"Content-2_PositionalContent-0\" data-visual-height=\"20.37pt\" data-visual-left=\"110pt\" data-visual-top=\"1994pt\" data-visual-width=\"191.64pt\" id=\"Content-2_PositionalContent-0\" style=\"color:#FFFFFF;font-family:Times;font-size:12pt;left:110pt;line-height:75%;margin-left:-5px;padding-left:5px;position:absolute;top:1994pt;white-space:nowrap\">Visible text because it is white on green</span>\n"
        +
        "    <div style=\"background-color:#00FF00;height:50pt;left:100pt;position:absolute;top:1984pt;width:300pt;z-index:-1\"></div>\n"
        +
        "   </div>\n" +
        "  </div>\n" +
        " </body>\n" +
        "</html>";

    assertEquals(expectedHtml, getRenderedHtml(document));
  }

  @Test
  public void testFormElement() throws Exception {
    Document document = new Document()
        .add(new Content(
            new Page()
                .add(new Content(
                    new FormElement()
                        .add(new FontSize(new Length(12, Unit.pt)))
                        .add(new Width(new Length(200, Unit.pt)))
                        .add(new FormData(new Form(FormType.TextField, null, null, null,
                            Lists.mutable.of("Example Of Text1"),
                            Maps.mutable.of(Form.IS_MULTILINE, false)))),
                    new FormElement()
                        .add(new FormData(
                            new Form(FormType.CheckBox, Lists.mutable.of("Yes", ""), null,
                                Sets.mutable.of("Yes"), Lists.mutable.of("Yes"), null))),
                    new FormElement()
                        .add(new FontSize(new Length(16, Unit.pt)))
                        .add(new Width(new Length(400, Unit.pt)))
                        .add(new Height(new Length(600, Unit.pt)))
                        .add(new FormData(new Form(FormType.TextField, null, null, null,
                            Lists.mutable.of("Example Of Text2"),
                            Maps.mutable.of(Form.IS_MULTILINE, true)))),
                    new FormElement()
                        .add(new FormData(
                            new Form(FormType.RadioButton, Lists.mutable.of("Abc", ""), null,
                                Sets.mutable.of("Abc", "Xyz"), Lists.mutable.of("Abc"), null))),
                    new FormElement()
                        .add(new Width(new Length(200, Unit.pt)))
                        .add(new FontSize(new Length(16, Unit.pt)))
                        .add(new FormData(new Form(FormType.ComboBox,
                            Lists.mutable.of("Banana", "Apple", "Papaya"),
                            Lists.mutable.of("Banana", "Apple", "Papaya"), null,
                            Lists.mutable.of("Apple", "Orange"),
                            Maps.mutable.of(Form.IS_MULTSELECT, true)))),
                    new FormElement()
                        .add(new Width(new Length(300, Unit.pt)))
                        .add(new FormData(new Form(FormType.SignatureField, null, null, null,
                            Lists.mutable.of("28632646363656565746576543654736"), null)))
                ))
        ));

    String expectedHtml = "<html>\n" +
        " <head>\n" +
        "  <meta charset=\"UTF-8\">\n" +
        " </head>\n" +
        " <body>\n" +
        "  <div>\n" +
        "   <div style=\"border-color:#d3d3d3;border-style:solid;box-sizing:border-box\">\n" +
        "    <input readonly style=\"font-size:12pt;width:200pt\" type=\"text\" value=\"Example Of Text1\">\n"
        +
        "    <input checked onclick=\"return false;\" type=\"checkbox\">\n" +
        "    <textarea readonly style=\"font-size:16pt;height:600pt;width:400pt\">Example Of Text2</textarea>\n"
        +
        "    <input checked onclick=\"return false;\" type=\"radio\">\n" +
        "    <select multiple style=\"font-size:16pt;width:200pt\"><option disabled>Banana</option><option disabled selected>Apple</option><option disabled>Papaya</option><option disabled selected>Orange</option></select>\n"
        +
        "    <input readonly style=\"width:300pt\" type=\"text\" value=\"\">\n" +
        "   </div>\n" +
        "  </div>\n" +
        " </body>\n" +
        "</html>";
    assertEquals(expectedHtml, getRenderedHtml(document));
  }

  @Test
  public void testSortHtmlNodeAttributes() {
    org.jsoup.nodes.Element node1 = new org.jsoup.nodes.Element(Tag.valueOf("div"), "")
        .attr("abc", "1")
        .attr("xyz", "val")
        .appendElement("p")
        .attr("abc", "2")
        .attr("xyz", "val2")
        .parent();

    org.jsoup.nodes.Element node2 = new org.jsoup.nodes.Element(Tag.valueOf("div"), "")
        .attr("xyz", "val")
        .attr("abc", "1")
        .appendElement("p")
        .attr("xyz", "val2")
        .attr("abc", "2")
        .parent();

    assertFalse(node1.hasSameValue(node2));

    HtmlRenderer.sortHtmlNodeAttributes(node1);
    HtmlRenderer.sortHtmlNodeAttributes(node2);
    assertTrue(node1.hasSameValue(node2));
  }

  @Test
  public void testTableWithFirstCellEmpty() throws Exception {
    PositionalDocDrawer drawer = new PositionalDocDrawer(PDRectangle.LETTER);
    GroupedBoundingBox tableBox = new GroupedBoundingBox(100, 100, 3, 4, 60, 20);
    MutableList<MutableList<String>> table = Lists.mutable.of(
        Lists.mutable.of("", "India", "France"),
        Lists.mutable.of("Cricket", "98", "85"),
        Lists.mutable.of("Football", "89", "95"));
    tableBox.forEachCellBBox(0, 2, 0, 2,
        (row, col, bbox) -> drawer.drawTextWithBorderInside(bbox, table.get(row).get(col)));
    String expectedHtml = "<html>\n" +
        " <head>\n" +
        "  <meta charset=\"UTF-8\">\n" +
        " </head>\n" +
        " <body>\n" +
        "  <div id=\"Root\" style=\"position:absolute\">\n" +
        "   <div id=\"Content-0\" style=\"border-color:#d3d3d3;border-style:solid;box-sizing:border-box;height:792pt;width:612pt\">\n"
        +
        "    <div style=\"background-color:#d3d3d3;height:60pt;left:100pt;position:absolute;top:100pt;width:1px;z-index:-1\"></div>\n"
        +
        "    <div style=\"background-color:#d3d3d3;height:1px;left:100pt;position:absolute;top:100pt;width:180pt;z-index:-1\"></div>\n"
        +
        "    <div style=\"background-color:#d3d3d3;height:60pt;left:160pt;position:absolute;top:100pt;width:1px;z-index:-1\"></div>\n"
        +
        "    <div style=\"background-color:#d3d3d3;height:60pt;left:220pt;position:absolute;top:100pt;width:1px;z-index:-1\"></div>\n"
        +
        "    <div style=\"background-color:#d3d3d3;height:60pt;left:280pt;position:absolute;top:100pt;width:1px;z-index:-1\"></div>\n"
        +
        "    <span id=\"Content-0_PositionalContent-5\" style=\"font-family:Times;font-size:12pt;left:165pt;line-height:75%;margin-left:-5px;padding-left:5px;position:absolute;top:105pt;white-space:nowrap\">India</span>\n"
        +
        "    <span id=\"Content-0_PositionalContent-6\" style=\"font-family:Times;font-size:12pt;left:225pt;line-height:75%;margin-left:-5px;padding-left:5px;position:absolute;top:105pt;white-space:nowrap\">France</span>\n"
        +
        "    <div style=\"background-color:#d3d3d3;height:1px;left:100pt;position:absolute;top:120pt;width:180pt;z-index:-1\"></div>\n"
        +
        "    <span id=\"Content-0_PositionalContent-8\" style=\"font-family:Times;font-size:12pt;left:105pt;line-height:75%;margin-left:-5px;padding-left:5px;position:absolute;top:125pt;white-space:nowrap\">Cricket</span>\n"
        +
        "    <span id=\"Content-0_PositionalContent-9\" style=\"font-family:Times;font-size:12pt;left:165pt;line-height:75%;margin-left:-5px;padding-left:5px;position:absolute;top:125pt;white-space:nowrap\">98</span>\n"
        +
        "    <span id=\"Content-0_PositionalContent-10\" style=\"font-family:Times;font-size:12pt;left:225pt;line-height:75%;margin-left:-5px;padding-left:5px;position:absolute;top:125pt;white-space:nowrap\">85</span>\n"
        +
        "    <div style=\"background-color:#d3d3d3;height:1px;left:100pt;position:absolute;top:140pt;width:180pt;z-index:-1\"></div>\n"
        +
        "    <span id=\"Content-0_PositionalContent-12\" style=\"font-family:Times;font-size:12pt;left:105pt;line-height:75%;margin-left:-5px;padding-left:5px;position:absolute;top:145pt;white-space:nowrap\">Football</span>\n"
        +
        "    <span id=\"Content-0_PositionalContent-13\" style=\"font-family:Times;font-size:12pt;left:165pt;line-height:75%;margin-left:-5px;padding-left:5px;position:absolute;top:145pt;white-space:nowrap\">89</span>\n"
        +
        "    <span id=\"Content-0_PositionalContent-14\" style=\"font-family:Times;font-size:12pt;left:225pt;line-height:75%;margin-left:-5px;padding-left:5px;position:absolute;top:145pt;white-space:nowrap\">95</span>\n"
        +
        "    <div style=\"background-color:#d3d3d3;height:1px;left:100pt;position:absolute;top:160pt;width:180pt;z-index:-1\"></div>\n"
        +
        "   </div>\n" +
        "  </div>\n" +
        " </body>\n" +
        "</html>";
    assertEquals(expectedHtml, getRenderedHtml(drawer.getDocument()));
  }

  @Test
  public void testBlankDocuments() throws Exception {
    Document document = new Document();
    String expectedHtml = "<html>\n" +
        " <head>\n" +
        "  <meta charset=\"UTF-8\">\n" +
        " </head>\n" +
        " <body>\n" +
        "  <div></div>\n" +
        " </body>\n" +
        "</html>";
    assertEquals(expectedHtml, getRenderedHtml(document));

    document = new Document().add(new Content());
    assertEquals(expectedHtml, getRenderedHtml(document));

    document = new Document().add(new Content(new Page()));
    expectedHtml = "<html>\n" +
        " <head>\n" +
        "  <meta charset=\"UTF-8\">\n" +
        " </head>\n" +
        " <body>\n" +
        "  <div>\n" +
        "   <div style=\"border-color:#d3d3d3;border-style:solid;box-sizing:border-box\"></div>\n" +
        "  </div>\n" +
        " </body>\n" +
        "</html>";
    assertEquals(expectedHtml, getRenderedHtml(document));

    document = new Document().add(new Content(new Page()
        .add(new PositionalContent(new PositionalElementList<Element>(Lists.mutable.empty())))));
    expectedHtml = "<html>\n" +
        " <head>\n" +
        "  <meta charset=\"UTF-8\">\n" +
        " </head>\n" +
        " <body>\n" +
        "  <div id=\"Root\" style=\"position:absolute\">\n" +
        "   <div id=\"Content-0\" style=\"border-color:#d3d3d3;border-style:solid;box-sizing:border-box\"></div>\n"
        +
        "  </div>\n" +
        " </body>\n" +
        "</html>";
    assertEquals(expectedHtml, getRenderedHtml(document));
  }

  @Test
  public void testPageBreak() throws Exception {
    Document document = new Document()
        .add(new PageStructure(PageStructure.NO_PAGE_BREAK))
        .add(new Content(
            new TextElement().add(new Text("abc")),
            new PageBreak(),
            new TextElement().add(new Text("xyz"))));
    String expectedHtml = "<html>\n" +
        " <head>\n" +
        "  <meta charset=\"UTF-8\">\n" +
        " </head>\n" +
        " <body>\n" +
        "  <div>\n" +
        "   <span>abc</span>\n" +
        "   <div style=\"background-color:#d3d3d3;height:4pt;width:inherit\"></div>\n" +
        "   <span>xyz</span>\n" +
        "  </div>\n" +
        " </body>\n" +
        "</html>";
    assertEquals(expectedHtml, getRenderedHtml(document));

    document = new Document()
        .add(new PageStructure(PageStructure.NO_PAGE_BREAK))
        .add(new Content(
            new Page()
                .add(new Width(new Length(50, Unit.pt)))
                .add(new Height(new Length(70, Unit.pt)))
                .add(new PositionalContent(
                    new TextElement()
                        .add(new Text("abc"))
                        .add(new Top(new Length(10, Unit.pt)))
                        .add(new Left(new Length(10, Unit.pt))),
                    new PageBreak()
                        .add(new Top(new Length(30, Unit.pt))),
                    new TextElement()
                        .add(new Text("xyz"))
                        .add(new Top(new Length(50, Unit.pt)))
                        .add(new Left(new Length(10, Unit.pt)))))));
    expectedHtml = "<html>\n" +
        " <head>\n" +
        "  <meta charset=\"UTF-8\">\n" +
        " </head>\n" +
        " <body>\n" +
        "  <div id=\"Root\" style=\"position:absolute\">\n" +
        "   <div id=\"Content-0\" style=\"border-color:#d3d3d3;border-style:solid;box-sizing:border-box;height:70pt;width:50pt\">\n"
        +
        "    <span id=\"Content-0_PositionalContent-0\" style=\"left:10pt;line-height:75%;margin-left:-5px;padding-left:5px;position:absolute;top:10pt;white-space:nowrap\">abc</span>\n"
        +
        "    <div style=\"background-color:#d3d3d3;height:4pt;left:0pt;margin-top:-2pt;position:absolute;top:30pt;width:inherit;z-index:-1\"></div>\n"
        +
        "    <span id=\"Content-0_PositionalContent-2\" style=\"left:10pt;line-height:75%;margin-left:-5px;padding-left:5px;position:absolute;top:50pt;white-space:nowrap\">xyz</span>\n"
        +
        "   </div>\n" +
        "  </div>\n" +
        " </body>\n" +
        "</html>";
    assertEquals(expectedHtml, getRenderedHtml(document));
  }
}
