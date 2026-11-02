-- Keep same-document navigation inside the PDF, and make other local links portable.
function Link(link)
    if not link.target:match("^[%a][%w+.-]*:") and not link.target:match("^#") then
        link.target = "https://github.com/AY2627S1-CS2113-W13-4/tp/blob/master/docs/" .. link.target
    end
    return link
end
