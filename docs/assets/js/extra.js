// Initialize Mermaid with custom configuration
document.addEventListener('DOMContentLoaded', function() {
    if (typeof mermaid !== 'undefined') {
        mermaid.initialize({
            startOnLoad: true,
            theme: 'default',
            securityLevel: 'loose',
            flowchart: {
                useMaxWidth: true,
                htmlLabels: true,
                curve: 'basis'
            },
            sequence: {
                diagramMarginX: 50,
                diagramMarginY: 10,
                actorMargin: 50,
                width: 150,
                height: 65,
                boxMargin: 10,
                boxTextMargin: 5,
                noteMargin: 10,
                messageMargin: 35
            }
        });

        // Force render any mermaid diagrams
        setTimeout(function() {
            if (typeof mermaid.run === 'function') {
                mermaid.run({ querySelector: '.mermaid' });
            } else {
                mermaid.init(undefined, document.querySelectorAll('.mermaid'));
            }
        }, 1000);
    } else {
        console.error('Mermaid library not loaded');
    }
});

// Add PlantUML image error handling
document.addEventListener('DOMContentLoaded', function() {
    const plantumlImages = document.querySelectorAll('.plantuml img');

    plantumlImages.forEach(function(img) {
        img.onerror = function() {
            const container = img.parentElement;
            container.innerHTML = '<div class="diagram-error">Error loading PlantUML diagram. Please check your syntax or server connection.</div>';
        };
    });
});
// 生成ドキュメント（ER 図・JIG）は独立した HTML のサイトなので、別タブで開く。
// ナビのリンクには Markdown の属性（{:target="_blank"}）を付けられないため、ここで付ける。
document.addEventListener('DOMContentLoaded', function() {
    document.querySelectorAll('a[href*="/schemaspy-output/"], a[href*="/jig-output/"]').forEach(function(link) {
        link.setAttribute('target', '_blank');
        link.setAttribute('rel', 'noopener');
    });
});
