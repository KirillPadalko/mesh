import { useEffect, useRef, useState } from 'react';
import * as d3 from 'd3';
import { meshGraphManager } from '../core/mesh/MeshGraphManager';
import { identityManager } from '../core/crypto/IdentityManager';

interface NetworkMapProps {
    onBack: () => void;
}

interface Node extends d3.SimulationNodeDatum {
    id: string;
    group: number; // 0: Me, 1: L1, 2: L2
    label: string;
}

interface Link extends d3.SimulationLinkDatum<Node> {
    source: string | Node;
    target: string | Node;
}

export function NetworkMap({ onBack }: NetworkMapProps) {
    const svgRef = useRef<SVGSVGElement>(null);
    const [stats, setStats] = useState({ nodes: 0, l1: 0, l2: 0 });

    useEffect(() => {
        if (!svgRef.current) return;

        const width = window.innerWidth;
        const height = window.innerHeight - 100; // Account for header

        // Clear previous
        d3.select(svgRef.current).selectAll("*").remove();

        const svg = d3.select(svgRef.current)
            .attr("width", "100%")
            .attr("height", height)
            .attr("viewBox", [0, 0, width, height]);

        // Add zoom
        const g = svg.append("g");
        svg.call(d3.zoom<SVGSVGElement, unknown>().on("zoom", (event) => {
            g.attr("transform", event.transform);
        }));

        const loadGraph = async () => {
            const myId = await identityManager.getMeshId();
            if (!myId) return;

            const l1 = meshGraphManager.getL1Connections();
            const l2 = meshGraphManager.getL2Connections();

            const nodes: Node[] = [];
            const links: Link[] = [];

            // Add Me
            nodes.push({ id: myId, group: 0, label: 'Me' });

            // Add L1
            l1.forEach(id => {
                nodes.push({ id, group: 1, label: id.substring(0, 4) });
                links.push({ source: myId, target: id });
            });

            // Add L2
            l2.forEach((children, parent) => {
                if (!l1.has(parent)) {
                    // This shouldn't happen based on logic, but safety check
                    // If parent isn't in nodes, add it (should be covered by L1)
                    if (!nodes.find(n => n.id === parent)) {
                        nodes.push({ id: parent, group: 1, label: parent.substring(0, 4) });
                        links.push({ source: myId, target: parent });
                    }
                }

                children.forEach(child => {
                    if (!nodes.find(n => n.id === child)) {
                        nodes.push({ id: child, group: 2, label: child.substring(0, 4) });
                    }
                    // Avoid duplicate links
                    if (!links.find(l => (l.source === parent && l.target === child) || (l.source === child && l.target === parent))) {
                        links.push({ source: parent, target: child });
                    }
                });
            });

            setStats({
                nodes: nodes.length,
                l1: l1.size,
                l2: nodes.length - l1.size - 1 // Approx
            });

            // Simulation
            const simulation = d3.forceSimulation(nodes)
                .force("link", d3.forceLink(links).id((d: any) => d.id).distance(100))
                .force("charge", d3.forceManyBody().strength(-300))
                .force("center", d3.forceCenter(width / 2, height / 2));

            // Render Links
            const link = g.append("g")
                .attr("stroke", "#475569")
                .attr("stroke-opacity", 0.6)
                .selectAll("line")
                .data(links)
                .join("line")
                .attr("stroke-width", 2);

            // Render Nodes
            const node = g.append("g")
                .selectAll("g")
                .data(nodes)
                .join("g")
                .call(d3.drag<any, any>()
                    .on("start", (event) => {
                        if (!event.active) simulation.alphaTarget(0.3).restart();
                        event.subject.fx = event.subject.x;
                        event.subject.fy = event.subject.y;
                    })
                    .on("drag", (event) => {
                        event.subject.fx = event.x;
                        event.subject.fy = event.y;
                    })
                    .on("end", (event) => {
                        if (!event.active) simulation.alphaTarget(0);
                        event.subject.fx = null;
                        event.subject.fy = null;
                    }));

            node.append("circle")
                .attr("r", d => d.group === 0 ? 10 : (d.group === 1 ? 8 : 5))
                .attr("fill", d => d.group === 0 ? "#ef4444" : (d.group === 1 ? "#38bdf8" : "#94a3b8"))
                .attr("stroke", "#fff")
                .attr("stroke-width", 1.5);

            node.append("text")
                .attr("dx", 12)
                .attr("dy", 4)
                .text(d => d.label)
                .attr("fill", "#e2e8f0")
                .style("font-size", "12px")
                .style("pointer-events", "none");

            // Tick
            simulation.on("tick", () => {
                link
                    .attr("x1", (d: any) => d.source.x)
                    .attr("y1", (d: any) => d.source.y)
                    .attr("x2", (d: any) => d.target.x)
                    .attr("y2", (d: any) => d.target.y);

                node
                    .attr("transform", (d: any) => `translate(${d.x},${d.y})`);
            });
        };

        loadGraph();

    }, []);

    return (
        <div style={{ width: '100%', height: '100vh', background: 'var(--background)', position: 'relative', overflow: 'hidden' }}>
            <div style={{
                position: 'absolute',
                top: 20,
                left: 20,
                zIndex: 10,
                background: 'var(--surface)',
                padding: '16px',
                borderRadius: 'var(--radius)',
                border: '1px solid var(--border)',
                boxShadow: 'var(--shadow)'
            }}>
                <button
                    onClick={onBack}
                    style={{
                        background: 'transparent',
                        border: '1px solid var(--border)',
                        color: 'var(--text)',
                        padding: '6px 12px',
                        borderRadius: 'var(--radius)',
                        marginBottom: '12px',
                        cursor: 'pointer',
                        fontSize: '13px'
                    }}
                >
                    Back
                </button>
                <h3 style={{ margin: '0 0 12px 0', color: 'var(--text)', fontSize: '16px' }}>Mesh Network</h3>
                <div style={{ color: 'var(--text-secondary)', fontSize: '13px', display: 'flex', flexDirection: 'column', gap: '4px' }}>
                    <div>Nodes: <span style={{ color: 'var(--primary)', fontWeight: '600' }}>{stats.nodes}</span></div>
                    <div>L1 Friends: <span style={{ color: 'var(--primary)', fontWeight: '600' }}>{stats.l1}</span></div>
                    <div>L2 Extended: <span style={{ color: 'var(--primary)', fontWeight: '600' }}>{stats.l2}</span></div>
                </div>
            </div>

            <svg ref={svgRef} style={{ width: '100%', height: '100%' }}></svg>

            <div style={{
                position: 'absolute',
                bottom: 20,
                right: 20,
                color: 'var(--text-secondary)',
                fontSize: '12px',
                background: 'var(--surface)',
                padding: '8px 12px',
                borderRadius: 'var(--radius)',
                border: '1px solid var(--border)'
            }}>
                <span style={{ display: 'inline-block', width: 8, height: 8, background: 'var(--warning)', borderRadius: '50%', marginRight: 6 }}></span> Me
                <span style={{ display: 'inline-block', width: 8, height: 8, background: 'var(--primary)', borderRadius: '50%', margin: '0 6px 0 16px' }}></span> Friend
                <span style={{ display: 'inline-block', width: 8, height: 8, background: 'var(--surface-raised)', borderRadius: '50%', margin: '0 6px 0 16px' }}></span> Connection
            </div>
        </div>
    );
}
