<%--@elvariable id="author" type="groovymvc.Author"--%>
<%--@elvariable id="g" type="groovymvc.template.TemplateSupport"--%>
<!DOCTYPE html>
<html>
<head>
    <title>Show</title>
    <meta name="layout" content="author/main"/>
</head>
<body>
Author: ${author.name}
Flash message: ${g.flash.message}
</body>
</html>