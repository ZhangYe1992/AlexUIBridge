#!/usr/bin/env python3
"""
Alex UI Bridge - 纯 HTTP 客户端
无需 ADB，完全通过 HTTP 控制
"""

import requests
import json
import time
from typing import List, Dict, Optional, Tuple

HTTP_BASE_URL = "http://localhost:8080"


class AlexUIBridgeClient:
    """
    纯 HTTP 客户端
    - 获取UI树: GET /dump
    - 点击: POST /tap {"x": 100, "y": 200}
    - 滑动: POST /swipe {"x1": 100, "y1": 500, "x2": 100, "y2": 100}
    - 返回: POST /back
    - 主页: POST /home
    - 电源: POST /power
    """
    
    def __init__(self, base_url: str = HTTP_BASE_URL):
        self.base_url = base_url
        self.screen_width = 1080
        self.screen_height = 2340
    
    def ping(self) -> bool:
        """检查服务是否运行"""
        try:
            r = requests.get(f"{self.base_url}/ping", timeout=2)
            return r.json().get("status") == "ok"
        except:
            return False
    
    def get_ui_tree(self) -> List[Dict]:
        """获取UI树"""
        try:
            r = requests.get(f"{self.base_url}/dump", timeout=5)
            return r.json()
        except Exception as e:
            print(f"获取UI树失败: {e}")
            return []
    
    def see_text(self) -> str:
        """以文本形式查看可点击元素"""
        start = time.time()
        elements = self.get_ui_tree()
        elapsed = (time.time() - start) * 1000
        
        if not elements:
            return "❌ 无法获取屏幕内容"
        
        lines = [f"📱 当前屏幕 ({len(elements)} 个元素, {elapsed:.0f}ms):", "=" * 50]
        
        # 显示可点击的元素
        clickable = [e for e in elements if e.get('clickable')]
        
        for i, elem in enumerate(clickable[:15], 1):
            text = elem.get('text', '') or elem.get('desc', '') or '[无文本]'
            text = text[:20]
            cx, cy = elem.get('cx', 0), elem.get('cy', 0)
            lines.append(f"{i}. {text} @ ({cx}, {cy})")
        
        if len(clickable) > 15:
            lines.append(f"... 还有 {len(clickable) - 15} 个")
        
        return "\n".join(lines)
    
    def tap(self, x: int, y: int) -> bool:
        """点击坐标"""
        print(f"👆 点击 ({x}, {y})")
        try:
            r = requests.post(f"{self.base_url}/tap", 
                            json={"x": x, "y": y}, 
                            timeout=5)
            result = r.json()
            time.sleep(0.3)
            return result.get("ok", False)
        except Exception as e:
            print(f"点击失败: {e}")
            return False
    
    def tap_text(self, text: str) -> bool:
        """根据文本点击"""
        elements = self.get_ui_tree()
        for elem in elements:
            elem_text = elem.get('text', '') or elem.get('desc', '')
            if text in elem_text:
                cx, cy = elem.get('cx'), elem.get('cy')
                if cx and cy:
                    print(f"🎯 找到'{text}'，点击 ({cx}, {cy})")
                    return self.tap(cx, cy)
        print(f"❌ 未找到文本: {text}")
        return False
    
    def swipe(self, x1: int, y1: int, x2: int, y2: int, duration: int = 300) -> bool:
        """滑动"""
        print(f"👆 滑动 ({x1},{y1}) → ({x2},{y2})")
        try:
            r = requests.post(f"{self.base_url}/swipe",
                            json={"x1": x1, "y1": y1, "x2": x2, "y2": y2, "duration": duration},
                            timeout=5)
            result = r.json()
            time.sleep(0.3)
            return result.get("ok", False)
        except Exception as e:
            print(f"滑动失败: {e}")
            return False
    
    def swipe_up(self, times: int = 1, duration: int = 500) -> bool:
        """上滑"""
        cx = int(1080 * 0.6)  # 假设 1080 宽度
        y1 = int(2340 * 0.65)  # 假设 2340 高度
        y2 = int(2340 * 0.35)
        
        for _ in range(times):
            self.swipe(cx, y1, cx, y2, duration)
            time.sleep(0.3)
        return True
    
    def swipe_down(self, times: int = 1, duration: int = 500) -> bool:
        """下滑"""
        cx = int(1080 * 0.6)
        y1 = int(2340 * 0.35)
        y2 = int(2340 * 0.65)
        
        for _ in range(times):
            self.swipe(cx, y1, cx, y2, duration)
            time.sleep(0.3)
        return True
    
    def back(self) -> bool:
        """返回"""
        print("🔙 返回")
        try:
            r = requests.post(f"{self.base_url}/back", timeout=3)
            time.sleep(0.3)
            return r.json().get("ok", False)
        except Exception as e:
            print(f"返回失败: {e}")
            return False
    
    def home(self) -> bool:
        """主页"""
        print("🏠 主页")
        try:
            r = requests.post(f"{self.base_url}/home", timeout=3)
            time.sleep(0.3)
            return r.json().get("ok", False)
        except Exception as e:
            print(f"主页失败: {e}")
            return False
    
    def power(self) -> bool:
        """电源"""
        print("⚡ 电源")
        try:
            r = requests.post(f"{self.base_url}/power", timeout=3)
            return r.json().get("ok", False)
        except Exception as e:
            print(f"电源失败: {e}")
            return False


# 快捷函数
_client = None

def get_client() -> AlexUIBridgeClient:
    global _client
    if _client is None:
        _client = AlexUIBridgeClient()
    return _client

def see() -> List[Dict]:
    return get_client().get_ui_tree()

def see_text() -> str:
    return get_client().see_text()

def tap(x: int, y: int) -> bool:
    return get_client().tap(x, y)

def tap_text(text: str) -> bool:
    return get_client().tap_text(text)

def swipe_up(times: int = 1, duration: int = 500) -> bool:
    return get_client().swipe_up(times, duration)

def swipe_down(times: int = 1, duration: int = 500) -> bool:
    return get_client().swipe_down(times, duration)

def back() -> bool:
    return get_client().back()

def home() -> bool:
    return get_client().home()

def power() -> bool:
    return get_client().power()


if __name__ == "__main__":
    print("🤖 Alex UI Bridge HTTP 客户端")
    print("=" * 50)
    
    client = AlexUIBridgeClient()
    
    # 测试连接
    if client.ping():
        print("✅ 服务连接成功")
    else:
        print("❌ 服务未启动")
        exit(1)
    
    # 测试获取UI树
    print("\n" + client.see_text())
    
    # 测试点击
    print("\n测试点击屏幕中心...")
    client.tap(540, 1170)
    
    print("\n✅ 测试完成")
