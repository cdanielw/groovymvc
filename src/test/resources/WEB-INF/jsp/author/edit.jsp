<%--@elvariable id="g" type="groovymvc.template.TemplateSupport"--%>
<!DOCTYPE html>
<html>
<head>
    <title>Edit</title>
    <meta name="layout" content="author/main"/>
</head>
<body>
${g.errors.dateOfBirth[0].message}
${g.errors.dateOfBirth[0].invalidValue}
${g.errors['books[0].author.id'][0].message}
</body>
</html>