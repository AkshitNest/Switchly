import { useEffect, useRef } from "react";
import * as THREE from "three";
import { RoundedBoxGeometry } from "three/addons/geometries/RoundedBoxGeometry.js";

export default function ConsoleScene() {
  const host = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const el = host.current;
    if (!el || !el.clientWidth) return;
    let renderer: THREE.WebGLRenderer;
    try { renderer = new THREE.WebGLRenderer({ alpha: true, antialias: true, powerPreference: "low-power" }); }
    catch { return; }
    const scene = new THREE.Scene();
    const camera = new THREE.OrthographicCamera(-4, 4, 4, -4, .1, 50);
    camera.position.set(5.2, 7.6, 7);
    camera.lookAt(0, 0, 0);
    const resources: { dispose(): void }[] = [];
    function piece(width: number, depth: number, height: number, color: string, x: number, y: number, z: number, radius = .06) {
      const geometry = new RoundedBoxGeometry(width, height, depth, 3, radius);
      const material = new THREE.MeshStandardMaterial({ color, roughness: .65, metalness: .16 });
      const mesh = new THREE.Mesh(geometry, material);
      mesh.position.set(x, y, z);
      resources.push(geometry, material);
      scene.add(mesh);
    }
    piece(5.5, 3.9, .46, "#163a37", 0, -.17, 0, .18);
    piece(5.46, 3.86, .23, "#dddcc8", 0, .06, 0, .17);
    for (const [i, color] of ["#35806b", "#e39e4a", "#38587d"].entries()) {
      const x = (i - 1) * 1.5;
      piece(.79, 2.56, .08, "#a6ab9a", x, .19, -.1);
      piece(.21, 2.27, .09, "#283d39", x, .24, -.1);
      for (let k = 0; k < 7; k++) piece(.14, .028, .02, "#6e766c", x + .53, .19, -.98 + k * .3, .004);
      const z = [-.73, .12, .71][i];
      piece(.91, .69, .29, color, x, .39, z, .09);
      piece(.48, .036, .016, "#f1eddf", x, .546, z, .004);
      piece(.36, .12, .04, color, x, .2, 1.51);
    }
    scene.add(new THREE.HemisphereLight(0xfff9e8, 0x48656b, 2.6));
    const light = new THREE.DirectionalLight(0xffedca, 3.2);
    light.position.set(-4, 8, 4);
    scene.add(light);
    renderer.setPixelRatio(Math.min(devicePixelRatio, 1.75));
    el.append(renderer.domElement);
    el.parentElement?.classList.add("scene-ready");
    function draw() {
      if (!el || !el.clientHeight) return;
      const aspect = el.clientWidth / el.clientHeight;
      const height = Math.max(3.1, 3.9 / aspect);
      camera.left = -height * aspect; camera.right = height * aspect;
      camera.top = height; camera.bottom = -height;
      camera.updateProjectionMatrix();
      renderer.setSize(el.clientWidth, el.clientHeight);
      renderer.render(scene, camera);
    }
    const observer = new ResizeObserver(draw);
    observer.observe(el);
    draw();
    return () => {
      observer.disconnect();
      el.parentElement?.classList.remove("scene-ready");
      for (const resource of resources) resource.dispose();
      renderer.dispose();
      renderer.domElement.remove();
    };
  }, []);
  return <div ref={host} className="console-scene" aria-hidden="true" />;
}
